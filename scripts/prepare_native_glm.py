#!/usr/bin/env python3
"""Exact nonshipping Anchors GLM projection; original archives and Root are read-only inputs."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCOPE = ROOT / 'src/nativeFixture/glm-scope.json'
INDEX = 'data/forge/loot_modifiers/global_loot_modifiers.json'
ID = re.compile(r'[a-z0-9_.-]+:[a-z0-9_./-]+')


class GlmError(ValueError):
    """Fail-closed original graph/projection/ownership violation."""


def require(ok, message):
    if not ok:
        raise GlmError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def encode(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def read(path, inputs):
    path = Path(path).resolve()
    data = path.read_bytes()
    require(str(path) not in inputs or inputs[str(path)] == sha(data), 'Concurrent input change')
    inputs[str(path)] = sha(data)
    return data


def typed_ids(value):
    result = set()
    if isinstance(value, dict):
        for key, item in value.items():
            if key in ('condition', 'function', 'type') and isinstance(item, str) and ':' in item:
                result.add(key + '|' + item)
            result |= typed_ids(item)
    elif isinstance(value, list):
        for item in value:
            result |= typed_ids(item)
    return result


def components(data, coordinate, origin, depth=0):
    require(depth <= 4, 'Nested SDK depth exceeded')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate SDK archive entry')
        indexes, resources, classes = [], {}, {}
        for name in names:
            if re.fullmatch(r'data/[^/]+/loot_modifiers/global_loot_modifiers.json', name):
                raw = archive.read(name)
                index = json.loads(raw)
                require(isinstance(index.get('entries', []), list), 'Invalid original SDK index')
                require(all(isinstance(i, str) and ID.fullmatch(i) for i in index.get('entries', [])), 'Invalid declared ID')
                indexes.append({'resource': name, 'sha256': sha(raw), 'entries': index.get('entries', [])})
            elif re.fullmatch(r'data/[^/]+/loot_modifiers/.+\.json', name):
                resources[name] = archive.read(name)
            elif name.endswith('.class'):
                classes[name[:-6].replace('/', '.')] = sha(archive.read(name))
        result = [{'coordinate': coordinate, 'origin': origin, 'sha256': sha(data),
                   'indexes': indexes, 'resources': resources, 'classes': classes}]
        for name in names:
            if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                result += components(archive.read(name), coordinate, origin + '!/' + name, depth + 1)
    return result


def project(scope, profile, root_index, inventory, root_override):
    """Selection is declaration INTERSECT explicit Root membership, before resource/type checks."""
    spec = scope['profiles'][profile]
    root_ids = root_index['entries']
    require(root_index['replace'] is True and len(root_ids) == 226 and len(set(root_ids)) == 226,
            'Canonical Root226 policy missing/duplicate/changed schema')
    declarations = {}
    for component in inventory:
        for index in component['indexes']:
            for identifier in index['entries']:
                declarations.setdefault(identifier, []).append((component, index))
    selected = [identifier for identifier in root_ids if identifier in declarations]
    excluded = sorted(set(declarations) - set(root_ids))
    require(selected == spec['ordered_ids'], 'Exact Root-ordered own-domain membership drift')
    require(excluded == spec['explicit_exclusions'], 'Explicit Root exclusion drift')
    require(len(selected) == spec['expected_count'] == 16, 'Empty/full/out-of-domain projection')
    require(len(declarations) == len(selected) + 4, 'Original SDK declaration domain drift')
    rows, used, witnesses = [], set(), set()
    for identifier in selected:
        require(len(declarations[identifier]) == 1, 'Duplicate in-scope SDK declaration')
        namespace, path = identifier.split(':', 1)
        resource = f'data/{namespace}/loot_modifiers/{path}.json'
        candidates = [(component, component['resources'][resource]) for component in inventory if resource in component['resources']]
        require(len(candidates) == 1, 'Included resource missing/duplicate; never prune')
        require(not root_override(resource), 'Root-authored override requires explicit own closure review')
        component, raw = candidates[0]
        definition = json.loads(raw)
        codec = definition.get('type')
        require(codec in scope['outer_codecs'], 'Unclosed included outer codec; never prune')
        refs = typed_ids(definition)
        require(refs <= scope['typed_registration_owners'].keys(), 'Unclosed included nested type; never prune')
        used |= refs
        for role in refs:
            witnesses.add(scope['typed_registration_owners'][role]['registration_witness_class'])
        for owner_field in ('implementation_class', 'registration_witness_class'):
            name = scope['outer_codecs'][codec][owner_field]
            require(scope['original_class_witnesses'][name]['coordinate'] == component['coordinate'],
                    'Typed implementation/registration ownership differs from actual declaring data owner')
            witnesses.add(name)
        for role in refs:
            if role.startswith('condition|'):
                name = scope['typed_registration_owners'][role]['registration_witness_class']
                require(scope['original_class_witnesses'][name]['coordinate'] == 'mappedForgeSdk',
                        'These exact four checked condition registrations must bind the actual Forge/vanilla SDK')
        rows.append({'id': identifier, 'codec': codec, 'resource': resource, 'sha256': sha(raw),
                     'original_origin': component['origin'], 'data_owner_coordinate': component['coordinate']})
    require(sorted(used) == spec['typed_ids'], 'Exact in-scope typed dependency closure drift')
    # All reviewed helper/bus/registration classes present in this profile are also bound. These
    # hashes pin inspected registration instructions, not namespace/modId/class-existence guesses.
    coordinates = {component['coordinate'] for component in inventory}
    witnesses |= {name for name, item in scope['original_class_witnesses'].items() if item['coordinate'] in coordinates}
    for name in witnesses:
        expected = scope['original_class_witnesses'][name]
        candidates = [component['classes'][name] for component in inventory
                      if component['coordinate'] == expected['coordinate'] and name in component['classes']]
        require(candidates == [expected['sha256']], 'Actual registration/codec/bus bytecode missing/drifted: ' + name)
    omitted = []
    for identifier in excluded:
        namespace, key = identifier.split(':', 1)
        resource = f'data/{namespace}/loot_modifiers/{key}.json'
        omitted.append({'id': identifier, 'reason': 'explicit_canonical_Root_nonmembership',
                        'original_definition_missing': not any(resource in item['resources'] for item in inventory)})
    return {'ordered_ids': selected, 'explicit_exclusions': excluded, 'excluded_original_resources': omitted, 'rows': rows,
            'typed_ids': sorted(used), 'registration_class_witnesses': sorted(witnesses),
            'original_index_sha256s': sorted({index['sha256'] for component in inventory for index in component['indexes']}),
            'root_entries_outside_domain_informational_only': len(set(root_ids) - declarations.keys()),
            'root_ordered_entries_outside_domain': [identifier for identifier in root_ids if identifier not in declarations],
            'gameplay_effect_acceptance': False}


def prepare(bindings_path, pack_root, profile, output):
    """Existing A recipe, B's actual raw graph/16/13 only. No operator evidence input."""
    inputs = {}
    require(profile == 'anchors', 'Only the original Anchors graph')
    import uuid
    require(output.is_absolute() and output.name == 'glm' and
            str(uuid.UUID(output.parent.name)) == output.parent.name and
            output.parent.parent.name == '.native-fixture-inputs', 'Expected actual owning per-UUID GLM component')
    require(not any(p.is_symlink() for p in [output, *output.parents]), 'Symlinked fixture output ancestry')
    require(not output.exists(), 'Refuse reused/partial GLM output; retain unchanged')
    bindings = json.loads(read(bindings_path, inputs))
    for relative in ('build.gradle.kts', 'gradle/native-canonical-policy.gradle', 'gradle/native-glm.gradle',
        'scripts/native_canonical_policy.py', 'scripts/prepare_native_glm.py',
        'src/nativeFixture/resources/bc_anchors_fixture_glm.mixins.json',
        'src/nativeFixture/java/com/bettercontent/bettermagicchunkanchors/nativefixture/glm/AnchorsFixtureGlm.java',
        'src/nativeFixture/java/com/bettercontent/bettermagicchunkanchors/nativefixture/glm/DecodedRowProofRules.java',
        'src/nativeFixture/java/com/bettercontent/bettermagicchunkanchors/nativefixture/glm/ArsDispatchScope.java',
        'src/nativeFixture/java/com/bettercontent/bettermagicchunkanchors/nativefixture/glmmixin/RegistryCodecDecodeMixin.java',
        'src/nativeFixture/java/com/bettercontent/bettermagicchunkanchors/nativefixture/glmmixin/RootCodecBodyMixin.java',
        'src/nativeFixture/java/com/bettercontent/bettermagicchunkanchors/nativefixture/glmmixin/FixtureGlmLoadMixin.java'):
        read(ROOT / relative, inputs)
    scope_bytes = read(SCOPE, inputs)
    scope = json.loads(scope_bytes)
    require(scope['schema'] == 'bc.anchors.fixture_glm_scope.v1', 'Owned scope schema')
    import canonical_sdk_roles
    role_spec = json.loads(read(ROOT / 'gradle/native-sdk-roles.json', inputs))
    for name in ('scripts/canonical_sdk_roles.py', 'gradle/native-sdk-roles.gradle', 'gradle/native-canonical-providers.gradle'):
        read(ROOT / name, inputs)
    sdk_roles = canonical_sdk_roles.validate(bindings, role_spec, lambda path: read(path, inputs))
    expected = scope['profiles'][profile]['originals']
    actual = bindings['originals']
    require([x['coordinate'] for x in actual] == [x['coordinate'] for x in expected], 'Actual original SDK graph differs')
    inventory = []
    originals_by_coordinate = {}
    declared = sorted({':'.join(x['coordinate'].split(':')[:3]) for x in expected})
    require(bindings['actual_declared_original_coordinates'] == declared, 'Actual declared original graph drifted')
    for item, spec in zip(actual, expected):
        raw = read(item['path'], inputs)
        require(sha(raw) == spec['sha256'], 'Original SDK archive differs: ' + spec['coordinate'])
        originals_by_coordinate[':'.join(spec['coordinate'].split(':')[:3])] = raw
        if spec['root_pin']:
            pin = tomllib.loads(read(pack_root / spec['root_pin'], inputs).decode())
            require(pin['download']['hash-format'] == 'sha1' and hashlib.sha1(raw).hexdigest() == pin['download']['hash'], 'Root pin does not bind original SDK')
        inventory += components(raw, spec['coordinate'], str(Path(item['path']).resolve()))
    require(sorted(x['coordinate'] for x in bindings['supplemental']) == ['actualDfuSdk','actualGuavaSdk','mappedForgeSdk'], 'Exact pinned supporting SDK set')
    for item in bindings['supplemental']:
        inventory += components(read(item['path'], inputs), item['coordinate'], str(Path(item['path']).resolve()))
    root_path = pack_root / 'kubejs' / INDEX
    root_bytes = read(root_path, inputs)
    require(sha(root_bytes) == scope['canonical_Root_index_sha256'], 'Existing authored Root policy bytes drifted')
    import subprocess
    require(subprocess.check_output(['git','-C',str(pack_root),'show','HEAD:kubejs/'+INDEX]) == root_bytes,
            'Canonical Root index must be actual tracked HEAD bytes; no whole-HEAD freeze')
    projection = project(scope, profile, json.loads(root_bytes), inventory,
                         lambda resource: (pack_root / 'kubejs' / resource).is_file())
    # Bind actual complete current runtime archive graph too, not merely a favorable subset of raw roots.
    runtime = []
    seen_originals = set()
    for item in bindings['runtime']:
        path = Path(item['path'])
        raw = read(path, inputs)
        runtime += components(raw, item['coordinate'], str(path.resolve()))
        if item['coordinate'] in originals_by_coordinate:
            seen_originals.add(item['coordinate'])
            if '_mapped_' in path.name:
                sidecar = dict(line.split('=',1) for line in read(Path(str(path)+'.input'), inputs).decode().splitlines() if '=' in line)
                require(sidecar.get('orig') == hashlib.sha1(originals_by_coordinate[item['coordinate']]).hexdigest(), 'Actual mapped runtime raw-origin drift')
            else:
                require(sha(raw) == sha(originals_by_coordinate[item['coordinate']]), 'Actual unmapped original archive drift')
    require(seen_originals == set(originals_by_coordinate), 'Actual runtime misses a declared original SDK root')
    for name, expected_hash in scope['runtime_class_witnesses'].items():
        found = [c['classes'][name] for c in runtime if name in c['classes']]
        require(found == [expected_hash], 'Actual pinned runtime decoder/ordering class missing/ambiguous/drifted: ' + name)
    peer_indexes = [index for role in sdk_roles['roles'] if role['role'] == 'canonical_producer'
                    for index in role.get('runtime_glm_indexes', {}).values()]
    require(all(index['entries'] == [] and index['replace'] is False for index in peer_indexes),
            'Nonempty custom GLM peer requires a separately reviewed domain expansion')
    original_index_stack = sorted([index['sha256'] for c in inventory for index in c['indexes']] +
                                  [index['sha256'] for index in peer_indexes])
    runtime_index_stack = sorted(index['sha256'] for c in runtime for index in c['indexes'])
    require(runtime_index_stack == original_index_stack, 'Actual runtime index graph/multiplicity differs; expose every extra index')
    declared = {x for c in runtime for index in c['indexes'] for x in index['entries']}
    required_domain = set(projection['ordered_ids']) | set(projection['explicit_exclusions'])
    require(declared == required_domain, 'Actual runtime declared SDK union differs; never prune additional declarations')
    for row in projection['rows']:
        matches = [c['resources'][row['resource']] for c in runtime if row['resource'] in c['resources']]
        require(len(matches) == 1 and sha(matches[0]) == row['sha256'], 'Actual runtime included data absent/duplicate/different')
    # Own processed source resources are an explicit domain input, not a namespace/existence filter.
    absent = [str((pack_root / 'kubejs' / row['resource']).resolve()) for row in projection['rows']]
    for directory in bindings['own_resource_directories']:
        path = Path(directory) / INDEX
        require(not path.exists(), 'New own index declaration requires explicit scope review')
        absent.append(str(path.resolve()))
    require(all(not Path(path).exists() for path in absent), 'Unexpected authored included override')
    index_bytes = encode({'replace': True, 'entries': projection['ordered_ids']})
    result = {'schema':'bc.anchors.fixture_glm_manifest.v1', 'profile':profile, 'attempt':output.parent.name,
        'pack_id':'zzzz_bc_source_anchors_malum_policy', 'scope_sha256':sha(scope_bytes),
        'source_controls_only': bool(bindings.get('source_control_only', False)),
        'canonical_root_index_sha256':sha(root_bytes), 'index_sha256':sha(index_bytes),
        'projection':projection, 'inputs':inputs, 'absent_root_overrides':absent,
        'sdk_role_binding':sdk_roles, 'sdk_bindings_path':str(Path(bindings_path).resolve())}
    verify_inputs(result)
    output.mkdir(parents=True)
    write_new(output/'pack'/INDEX, index_bytes)
    write_new(output/'pack/pack.mcmeta', encode({'pack':{'pack_format':15,'description':'Anchors nonshipping canonical GLM component'}}))
    write_new(output/'root-parent-global-loot-modifiers.json',root_bytes)
    write_new(output/'manifest.json',encode(result))
    verify(output/'manifest.json')
    return result


def write_new(path, data):
    import os
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as stream:
        stream.write(data); stream.flush(); os.fsync(stream.fileno())
    path.chmod(0o444)


def verify_inputs(data):
    for path, expected in data['inputs'].items():
        require(Path(path).is_file() and sha(Path(path).read_bytes()) == expected, 'Original/source input changed: ' + path)
    require(all(not Path(path).exists() for path in data['absent_root_overrides']), 'Root override appeared')


def verify(path):
    data = json.loads(path.read_bytes())
    verify_inputs(data)
    import canonical_sdk_roles
    require(canonical_sdk_roles.validate(json.loads(Path(data['sdk_bindings_path']).read_bytes()),
        json.loads((ROOT / 'gradle/native-sdk-roles.json').read_bytes())) == data['sdk_role_binding'], 'SDK role binding changed')
    scope = json.loads(SCOPE.read_bytes())
    require(data['schema'] == 'bc.anchors.fixture_glm_manifest.v1' and sha(SCOPE.read_bytes()) == data['scope_sha256'], 'Manifest/scope schema or hash drift')
    projection = data['projection']
    spec = scope['profiles'][data['profile']]
    require(projection['ordered_ids'] == spec['ordered_ids'], 'Manifest own domain drift')
    require(projection['explicit_exclusions'] == spec['explicit_exclusions'] and projection['typed_ids'] == spec['typed_ids'], 'Manifest authority/type closure drift')
    require([row['id'] for row in projection['rows']] == spec['ordered_ids'], 'Manifest resource coverage/order drift')
    for row in projection['rows']:
        namespace, key = row['id'].split(':', 1)
        require(row['resource'] == f'data/{namespace}/loot_modifiers/{key}.json' and
                row['codec'] in scope['outer_codecs'] and re.fullmatch('[a-f0-9]{64}', row['sha256']), 'Manifest included resource/codec drift')
    require(projection['gameplay_effect_acceptance'] is False, 'Loading is not gameplay-effect acceptance')
    require(sha((path.parent / 'root-parent-global-loot-modifiers.json').read_bytes()) == data['canonical_root_index_sha256'], 'Retained canonical parent differs')
    raw = (path.parent / 'pack' / INDEX).read_bytes()
    require(sha(raw) == data['index_sha256'] and json.loads(raw) == {'replace': True, 'entries': data['projection']['ordered_ids']}, 'Prepared fixed index drift')
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bindings',type=Path)
    parser.add_argument('--root',type=Path)
    parser.add_argument('--output',type=Path)
    parser.add_argument('--verify',type=Path)
    args=parser.parse_args()
    data=verify(args.verify) if args.verify else prepare(args.bindings,args.root,'anchors',args.output)
    print('Anchors exact16 canonical GLM source inputs verified; NOT native loading acceptance')

if __name__ == '__main__':
    main()
