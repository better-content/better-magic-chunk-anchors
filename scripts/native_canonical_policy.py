#!/usr/bin/env python3
"""Exact approved nonshipping Malum input transport; never a distribution assembler."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import uuid
import zipfile

MANIFEST_SHA256 = '07b0208546fdde2e1c74bd4d336a39b4a690d5ec2fd231904bbdc17269a36186'
PACK_ID = 'zzzz_bc_source_anchors_malum_policy'


class FixtureError(RuntimeError):
    pass


def digest(data):
    return hashlib.sha256(data).hexdigest()


def no_symlinks(path):
    if any(p.is_symlink() for p in [path.absolute(), *path.absolute().parents]):
        raise FixtureError('Symlinked canonical input/snapshot/destination')


def git(root, *args):
    result = subprocess.run(['git', '-C', str(root), *args], capture_output=True)
    if result.returncode:
        raise FixtureError('Unavailable/untracked canonical Git input')
    return result.stdout


def validate_inputs(root, snapshot):
    no_symlinks(root)
    no_symlinks(snapshot)
    raw = (snapshot / 'manifest.json').read_bytes()
    if digest(raw) != MANIFEST_SHA256:
        raise FixtureError('Unapproved immutable manifest')
    manifest = json.loads(raw)
    if manifest['count'] != 36 or len(manifest['files']) != 36:
        raise FixtureError('Canonical population must remain exactly36')
    expected = {'pack.mcmeta': manifest['fixture_pack_metadata_sha256']}
    ids = set()
    for row in manifest['files']:
        if row['resource_id'] in ids or row['pack_path'] in expected:
            raise FixtureError('Duplicate canonical resource')
        ids.add(row['resource_id'])
        expected[row['pack_path']] = row['sha256']
        author = root / row['authoring_path']
        no_symlinks(author)
        git(root, 'ls-files', '--error-unmatch', row['authoring_path'])
        data = author.read_bytes()
        if (digest(data) != row['sha256'] or git(root, 'show', 'HEAD:' + row['authoring_path']) != data
                or git(root, 'show', manifest['root_head'] + ':' + row['authoring_path']) != data
                or git(root, 'rev-parse', 'HEAD:' + row['authoring_path']).decode().strip() != row['git_blob']):
            raise FixtureError('Tracked original/current canonical policy changed: ' + row['authoring_path'])
    paths = list(snapshot.rglob('*'))
    if any(p.is_symlink() for p in paths):
        raise FixtureError('Symlinked snapshot member')
    actual = {str(p.relative_to(snapshot)) for p in paths if p.is_file()}
    if actual != {'manifest.json'} | {'pack/' + name for name in expected}:
        raise FixtureError('Snapshot contains missing/additional inputs')
    for name, sha in expected.items():
        if digest((snapshot / 'pack' / name).read_bytes()) != sha:
            raise FixtureError('Snapshot raw-byte mismatch: ' + name)
    return manifest, git(root, 'rev-parse', 'HEAD').decode().strip()


def validate_destination(destination, run):
    no_symlinks(destination)
    no_symlinks(run)
    try:
        if str(uuid.UUID(run.name)) != run.name:
            raise ValueError('noncanonical UUID')
    except ValueError as failure:
        raise FixtureError('Expected canonical actual native UUID') from failure
    if (run.parent.name != 'gametest' or run.parent.parent.name != 'build'
            or destination.name != run.name or destination.parent.name != '.native-fixture-inputs'
            or destination.parent.parent.resolve() != run.parent.parent.parent.resolve()):
        raise FixtureError('Inputs must be per-token outside clean, bound to this configured native run')
    if destination.exists():
        raise FixtureError('Refusing reused/partial input token; retained unchanged')


def write_new(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    path.chmod(0o444)


def materialize(root, snapshot, destination, run, registration, glm_bindings=None):
    manifest, head = validate_inputs(root, snapshot)
    validate_destination(destination, run)
    # Caller must register this exact retained boundary BEFORE this first write.
    destination.mkdir(parents=True, exist_ok=False)
    paths = ['manifest.json', 'pack/pack.mcmeta'] + ['pack/' + row['pack_path'] for row in manifest['files']]
    for name in paths:
        write_new(destination / name, (snapshot / name).read_bytes())
    glm_receipt = None
    if glm_bindings is not None:
        import importlib.util
        binding_path = destination / 'glm-bindings.json'
        write_new(binding_path, glm_bindings)
        spec = importlib.util.spec_from_file_location('anchors_glm', Path(__file__).with_name('prepare_native_glm.py'))
        glm = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(glm)
        projection = glm.prepare(binding_path, root, 'anchors', destination / 'glm')
        index = glm.INDEX
        write_new(destination / 'pack' / index, (destination / 'glm/pack' / index).read_bytes())
        glm_receipt = {'count': len(projection['projection']['ordered_ids']),
                       'manifest_sha256': digest((destination / 'glm/manifest.json').read_bytes()),
                       'index_sha256': projection['index_sha256']}
    receipt = {'phase': 'registered-before-write nonshipping canonical inputs',
               'utc': datetime.now(timezone.utc).isoformat(), 'actual_configured_run': str(run.resolve()),
               'token': run.name, 'pack_id': PACK_ID, 'manifest_sha256': MANIFEST_SHA256,
               'original_root_head': manifest['root_head'], 'current_root_head': head,
               'registration': registration, 'count': 36}
    if glm_receipt is not None:
        receipt['canonical_glm_component'] = glm_receipt
    write_new(destination / 'preparation.json', (json.dumps(receipt, indent=2) + '\n').encode())
    # Recheck the complete copied bytes/current scoped authority before any native consumer.
    validate_inputs(root, snapshot)
    for name in paths:
        if (destination / name).read_bytes() != (snapshot / name).read_bytes():
            raise FixtureError('Copied canonical input mismatch')
    for path in destination.rglob('*'):
        if path.is_file():
            with path.open('rb') as stream:
                os.fsync(stream.fileno())
    for directory in sorted([destination] + [p for p in destination.rglob('*') if p.is_dir()], key=lambda p: len(p.parts), reverse=True):
        directory.chmod(0o555)
        fd = os.open(directory, os.O_DIRECTORY)
        os.fsync(fd)
        os.close(fd)
    return receipt


def check_archives(paths):
    for path in paths:
        with zipfile.ZipFile(path) as archive:
            leaks = [name for name in archive.namelist() if any(part in name for part in
                     ['nativefixture/', 'canonical-malum-policy', 'native_canonical_policy', 'data/malum/',
                      'bc_anchors_fixture_glm', 'glm-scope.json', 'prepare_native_glm', 'test_native_glm',
                      'native-glm.gradle', 'native-canonical-policy.gradle', 'nativeFixture/',
                      'AnchorsFixtureGlm', 'FixtureGlmLoadMixin', 'DecodedRowProofRules',
                      'data/forge/loot_modifiers/global_loot_modifiers.json'])]
        if leaks:
            raise FixtureError('Nonshipping canonical fixture leaked; archive not filtered: ' + ', '.join(leaks))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['prepare', 'finish', 'archives'])
    parser.add_argument('--root', type=Path)
    parser.add_argument('--snapshot', type=Path)
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--run', type=Path)
    parser.add_argument('--owner')
    parser.add_argument('--archive', type=Path, action='append', default=[])
    parser.add_argument('--glm-bindings-stdin', action='store_true')
    args = parser.parse_args()
    if args.action == 'archives':
        if len(args.archive) != 2:
            parser.error('Need the actual runtime and sources archives')
        check_archives(args.archive)
        return
    if any(value is None for value in [args.root, args.destination, args.run, args.owner]):
        parser.error('prepare/finish require canonical root, destination, actual run and stable activity owner')
    task = 'source-anchors-canonical-' + args.run.name
    facade = args.root / 'maintenance.main.kts'
    command = [str(facade), args.action if args.action == 'finish' else 'begin', '--task', task, '--owner', args.owner]
    if args.action == 'finish':
        subprocess.run(command, cwd=args.root, check=True)
        return  # Closure only. No input/world/evidence/cache deletion.
    if args.snapshot is None:
        parser.error('prepare requires approved snapshot')
    if not args.glm_bindings_stdin:
        parser.error('Owning fresh preparation requires automatic actual-graph GLM bindings on stdin')
    import sys
    glm_bindings = sys.stdin.buffer.read(4 * 1024 * 1024 + 1)
    if not glm_bindings or len(glm_bindings) > 4 * 1024 * 1024:
        parser.error('Missing/overflow automatic actual-graph GLM bindings')
    validate_inputs(args.root, args.snapshot)
    validate_destination(args.destination, args.run)
    command += ['--path', str(args.destination.resolve()), '--no-build-cache']
    registered = subprocess.run(command, cwd=args.root, capture_output=True, text=True, check=True)
    materialize(args.root, args.snapshot, args.destination, args.run,
                {'command': command, 'exit': registered.returncode, 'stdout': registered.stdout}, glm_bindings)


if __name__ == '__main__':
    main()
