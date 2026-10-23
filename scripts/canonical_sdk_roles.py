"""Exact whole SDK roles; Root caller owns qualification, never inferred from a stamp."""
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile

sha = lambda data: hashlib.sha256(data).hexdigest()


def ordinary(path):
    path = Path(path).absolute()
    if any(p.is_symlink() for p in [path, *path.parents]) or not path.is_file():
        raise ValueError('Unsafe/missing SDK role input: ' + str(path))
    return path


def validate(binding, spec, read=lambda p: ordinary(p).read_bytes()):
    if spec.get('schema') != 'bc.canonical_sdk_roles.v1':
        raise ValueError('SDK role spec schema differs')
    expected = sorted(row['coordinate'] for row in spec['roles'])
    actual = binding['actual_declared_sdk_coordinates']
    duplicates = sorted({x for x in actual if actual.count(x) != 1})
    if len(expected) != len(set(expected)) or actual != expected or duplicates:
        raise ValueError('Whole declared SDK drift: ' + json.dumps(dict(expected=expected, actual=actual,
            missing=sorted(set(expected)-set(actual)), extra=sorted(set(actual)-set(expected)), duplicates=duplicates), sort_keys=True))
    files = binding.get('actual_declared_sdk_files', [])
    if files != spec.get('declared_file_dependencies', []) or len(files) != len(set(files)):
        raise ValueError('Declared SDK file dependency role differs')
    locations = binding['declared_sdk_configuration_roles']
    keys = [(r['configuration'], r['coordinate']) for r in locations]
    allowed_configs = {'implementation', 'runtimeOnly', 'nativeFixtureRuntimeOnly'} | set(spec.get('extra_declaration_configurations', []))
    if len(keys) != len(set(keys)) or sorted({r['coordinate'] for r in locations}) != actual or any(r['configuration'] not in allowed_configs for r in locations):
        raise ValueError('SDK configuration role coverage/duplicates differ')
    originals = sorted(r['coordinate'] for r in spec['roles'] if r['role'] != 'canonical_producer')
    if binding['actual_declared_original_coordinates'] != originals:
        raise ValueError('Original ROLE graph differs after whole graph admission')
    peer_coordinates = {r['coordinate'] for r in spec['roles'] if r['role'] == 'canonical_producer'}
    if any(r['coordinate'] in peer_coordinates and r['configuration'] != 'nativeFixtureRuntimeOnly' for r in locations):
        raise ValueError('Canonical producer escaped native-only declaration role')
    peers = binding['canonical_providers']
    wanted = [r for r in spec['roles'] if r['role'] == 'canonical_producer']
    if sorted(p['coordinate'] for p in peers) != sorted(r['coordinate'] for r in wanted):
        raise ValueError('Canonical producer ROLE graph differs')
    result = []
    for row in spec['roles']:
        if row['role'] not in ('upstream_original', 'upstream_api', 'canonical_producer'):
            raise ValueError('Unknown SDK role')
        item = dict(row)
        if row['role'] == 'canonical_producer':
            matches = [p for p in peers if p['coordinate'] == row['coordinate']]
            if len(matches) != 1:
                raise ValueError('Duplicate/missing canonical provider')
            peer = matches[0]
            if any(peer.get(k) != row[k] for k in ('role','repository','mod_id','artifact')):
                raise ValueError('Canonical provider binding role/identity differs')
            path = ordinary(peer['path'])
            if path.name != row['artifact'] or path.parent != Path(binding['canonical_staging']).absolute():
                raise ValueError('Canonical provider escaped bound staging')
            raw = read(path)
            if sha(raw) != peer['sha256']:
                raise ValueError('Canonical provider dynamic stage digest changed')
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                if len(archive.namelist()) != len(set(archive.namelist())):
                    raise ValueError('Duplicate canonical provider archive entry')
                name = 'META-INF/better-content-source.properties'
                if archive.namelist().count(name) != 1:
                    raise ValueError('Missing/duplicate canonical source metadata')
                metadata = archive.read(name)
                props = dict(line.split('=',1) for line in metadata.decode().splitlines() if '=' in line and not line.startswith('#'))
                if (props.get('schema') != 'bc.custom_mod_source.v1' or
                    any(props.get(k) != row[k] for k in ('repository','mod_id')) or
                    not re.fullmatch('[a-f0-9]{40}', props.get('commit','')) or props['commit'] != peer.get('commit') or
                    sha(metadata) != peer['source_metadata_sha256']):
                    raise ValueError('Canonical provider source schema/commit/binding changed')
                item['runtime_data_resources'] = {n: sha(archive.read(n)) for n in sorted(archive.namelist()) if n.startswith('data/') and not n.endswith('/')}
                indexes = sorted(n for n in archive.namelist() if re.fullmatch(r'data/[^/]+/loot_modifiers/global_loot_modifiers.json', n))
                # Explicitly reviewed empty peers remain in the runtime stack/multiplicity.
                observed_indexes = {n: dict(sha256=sha(archive.read(n)), **json.loads(archive.read(n))) for n in indexes}
                if observed_indexes != row.get('reviewed_glm_indexes', {}):
                    raise ValueError('Canonical runtime peer has unreviewed GLM content: ' + json.dumps(observed_indexes))
                item['runtime_glm_indexes'] = observed_indexes
            item.update(peer)
        result.append(item)
    apis = binding.get('upstream_api_archives', [])
    expected_apis = [r for r in spec['roles'] if r.get('explicit_api_archive')]
    if sorted(x['coordinate'] for x in apis) != sorted(x['coordinate'] for x in expected_apis):
        raise ValueError('Explicit upstream API archive role differs')
    for api in expected_apis:
        matches = [x for x in apis if x['coordinate'] == api['coordinate']]
        if len(matches) != 1 or sha(read(matches[0]['path'])) != api['sha256']:
            raise ValueError('Explicit upstream API archive digest differs')
        with zipfile.ZipFile(io.BytesIO(read(matches[0]['path']))) as archive:
            if any(re.fullmatch(r'data/[^/]+/loot_modifiers/global_loot_modifiers.json', n) for n in archive.namelist()):
                raise ValueError('API unexpectedly contributes GLM content')
    return dict(actual_declared_sdk_coordinates=actual, actual_declared_original_coordinates=originals,
        canonical_staging=binding['canonical_staging'], roles=result, declared_sdk_configuration_roles=locations,
        actual_declared_sdk_files=files,
        upstream_api_archives=apis, owning_gate_authority='trusted Root caller; metadata is not owning-gate proof')
