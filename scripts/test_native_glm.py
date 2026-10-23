#!/usr/bin/env python3
"""Actual-recipe pure controls, never Forge/Minecraft initialization or native acceptance."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
import uuid
import zipfile

REPO = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get('BC_CANONICAL_PACK_ROOT', '/home/dev/better-content-modpack'))
E = Path(os.environ.get('BC_ANCHORS_GLM_TEST_EVIDENCE', REPO / 'build/native-glm-controls')) / str(uuid.uuid4())
E.mkdir(parents=True)
def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value
p = module('anchors_glm', REPO / 'scripts/prepare_native_glm.py')
c = module('anchors_canonical', REPO / 'scripts/native_canonical_policy.py')
BINDINGS = json.loads(sys.stdin.buffer.read())
SCOPE = json.loads(p.SCOPE.read_bytes())
INVENTORY = []
for item in BINDINGS['originals'] + BINDINGS['supplemental']:
    INVENTORY += p.components(Path(item['path']).read_bytes(), item['coordinate'], item['path'])
ROOT_INDEX = json.loads((ROOT / 'kubejs' / p.INDEX).read_bytes())

class GlmRecipeControls(unittest.TestCase):
    def projection(self, scope=None, inventory=None, root=None, override=None):
        return p.project(scope or SCOPE, 'anchors', root or ROOT_INDEX, inventory or INVENTORY, override or (lambda resource: False))

    def test_actual18_roots_D20_Root16_typed13_and_all210_rows(self):
        data = self.projection()
        self.assertEqual(18, len(BINDINGS['originals']))
        self.assertEqual(SCOPE['profiles']['anchors']['ordered_ids'], data['ordered_ids'])
        self.assertEqual(16, len(data['rows']))
        self.assertEqual(13, len(data['typed_ids']))
        self.assertEqual(210, len(data['root_ordered_entries_outside_domain']))
        self.assertEqual(4, len(data['explicit_exclusions']))

    def test_wrong_order_rejected_by_actual_projection(self):
        scope = copy.deepcopy(SCOPE)
        scope['profiles']['anchors']['ordered_ids'][0:2] = reversed(scope['profiles']['anchors']['ordered_ids'][0:2])
        with self.assertRaises(p.GlmError): self.projection(scope=scope)

    def test_wrong_key_rejected_not_pruned(self):
        inventory = copy.deepcopy(INVENTORY)
        row = self.projection()['rows'][0]
        component = next(c for c in inventory if row['resource'] in c['resources'])
        definition = json.loads(component['resources'][row['resource']]); definition['type'] = 'ars_nouveau:wrong_key'
        component['resources'][row['resource']] = p.encode(definition)
        with self.assertRaises(p.GlmError): self.projection(inventory=inventory)

    def test_wrong_implementation_owner_rejected(self):
        scope = copy.deepcopy(SCOPE)
        scope['outer_codecs']['ars_nouveau:dungeon_loot']['implementation_class'] = 'me.desht.pneumaticcraft.common.loot.PNCDungeonLootModifier'
        with self.assertRaises(p.GlmError): self.projection(scope=scope)

    def test_wrong_registration_owner_rejected(self):
        scope = copy.deepcopy(SCOPE)
        scope['outer_codecs']['ars_nouveau:dungeon_loot']['registration_witness_class'] = 'me.desht.pneumaticcraft.common.core.ModLootModifiers'
        with self.assertRaises(p.GlmError): self.projection(scope=scope)

    def test_wrong_dispatch_bytecode_rejected(self):
        scope = copy.deepcopy(SCOPE)
        scope['original_class_witnesses']['net.minecraftforge.common.loot.IGlobalLootModifier']['sha256'] = '0' * 64
        with self.assertRaises(p.GlmError): self.projection(scope=scope)

    def test_missing_included_definition_rejected_not_pruned(self):
        inventory = copy.deepcopy(INVENTORY); resource = self.projection()['rows'][0]['resource']
        for component in inventory: component['resources'].pop(resource, None)
        with self.assertRaises(p.GlmError): self.projection(inventory=inventory)

    def test_unknown_condition_dependency_rejected(self):
        inventory = copy.deepcopy(INVENTORY); row = self.projection()['rows'][0]
        component = next(c for c in inventory if row['resource'] in c['resources'])
        definition = json.loads(component['resources'][row['resource']]); definition['conditions'].append({'condition': 'forge:unclosed_condition'})
        component['resources'][row['resource']] = p.encode(definition)
        with self.assertRaises(p.GlmError): self.projection(inventory=inventory)

    def test_additional_declaration_must_not_be_filtered(self):
        inventory = copy.deepcopy(INVENTORY)
        next(c for c in inventory if c['indexes'])['indexes'][0]['entries'].append('goety:unexpected_addition')
        with self.assertRaises(p.GlmError): self.projection(inventory=inventory)

    def test_root_authored_override_rejected(self):
        resource = self.projection()['rows'][0]['resource']
        with self.assertRaises(p.GlmError): self.projection(override=lambda path: path == resource)

    def test_omission_authority_does_not_depend_on_resource_absence(self):
        inventory = copy.deepcopy(INVENTORY)
        goety = next(c for c in inventory if c['coordinate'] == 'curse.maven:goety-586095:8087429')
        for identifier in SCOPE['profiles']['anchors']['explicit_exclusions']:
            namespace, key = identifier.split(':')
            goety['resources'][f'data/{namespace}/loot_modifiers/{key}.json'] = b'{"type":"synthetic:unused-control-only"}\n'
        data = self.projection(inventory=inventory)
        self.assertEqual(16, len(data['rows']))
        self.assertTrue(all(not row['original_definition_missing'] for row in data['excluded_original_resources']))

    def test_both_archive_roles_reject_every_new_fixture_shape(self):
        entries = ['bc_anchors_fixture_glm.mixins.json', 'bc_anchors_fixture_glm_scope.json',
                   'glm-scope.json', 'scripts/prepare_native_glm.py',
                   'com/bettercontent/bettermagicchunkanchors/nativefixture/glm/DecodedRowProofRules.class', p.INDEX]
        for role in ['runtime-reobf', 'sources']:
            for i, entry in enumerate(entries):
                path = E / f'{role}-leak-{i}.zip'
                with zipfile.ZipFile(path, 'x') as archive: archive.writestr(entry, b'SOURCE negative only')
                with self.assertRaises(c.FixtureError): c.check_archives([path])
                with zipfile.ZipFile(path) as archive: self.assertIn(entry, archive.namelist())

    def test_foreign_and_symlinked_and_partial_component_rejected(self):
        for name in ['foreign', 'partial', 'symlink']:
            parent = E / name / '.native-fixture-inputs' / str(uuid.uuid4())
            output = parent / ('other' if name == 'foreign' else 'glm')
            binding = E / (name + '-bindings.json'); binding.write_bytes(p.encode(BINDINGS))
            if name == 'partial': output.mkdir(parents=True); (output / 'partial').write_text('retain')
            if name == 'symlink':
                outside = E / 'outside'; outside.mkdir(); parent.mkdir(parents=True); output.symlink_to(outside, target_is_directory=True)
            with self.assertRaises((p.GlmError, ValueError)): p.prepare(binding, ROOT, 'anchors', output.resolve() if name != 'symlink' else output.absolute())

if __name__ == '__main__':
    print('Retained actual-recipe SOURCE controls, not native:', E, flush=True)
    unittest.main(verbosity=2, argv=[sys.argv[0]])
