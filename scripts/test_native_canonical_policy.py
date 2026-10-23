#!/usr/bin/env python3
"""Retained source-only controls for the exact approved canonical fixture transport."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import unittest
import uuid
import zipfile

spec = importlib.util.spec_from_file_location('policy', Path(__file__).with_name('native_canonical_policy.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)
REPO = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get('BC_CANONICAL_PACK_ROOT', '/home/dev/better-content-modpack'))
SNAPSHOT = REPO / 'gradle/canonical-malum-policy'
E = Path(os.environ.get('BC_ANCHORS_POLICY_TEST_EVIDENCE_DIR', REPO / 'build/native-canonical-controls')) / str(uuid.uuid4())
E.mkdir(parents=True)


class CanonicalInputs(unittest.TestCase):
    def layout(self):
        project = E / self._testMethodName / 'source-only-layout'
        token = str(uuid.uuid4())
        return project / '.native-fixture-inputs' / token, project / 'build/gametest' / token

    def test_exact_approved_raw_inputs(self):
        manifest, head = p.validate_inputs(ROOT, SNAPSHOT)
        self.assertEqual(36, len(manifest['files']))
        self.assertEqual(36, len({row['resource_id'] for row in manifest['files']}))
        self.assertEqual(p.MANIFEST_SHA256, p.digest((SNAPSHOT / 'manifest.json').read_bytes()))
        self.assertTrue(head)

    def test_corrupted_snapshot_rejected(self):
        snapshot = E / self._testMethodName
        shutil.copytree(SNAPSHOT, snapshot)
        manifest = json.loads((snapshot / 'manifest.json').read_text())
        path = snapshot / 'pack' / manifest['files'][0]['pack_path']
        path.chmod(0o644); path.write_bytes(b'{}\n')
        with self.assertRaises(p.FixtureError): p.validate_inputs(ROOT, snapshot)

    def test_transport_readonly_exact_and_no_world_preparation(self):
        destination, run = self.layout()
        receipt = p.materialize(ROOT, SNAPSHOT, destination, run,
                                {'exit': 0, 'source_control_only': True, 'retained_parent': str(E)})
        self.assertEqual(36, receipt['count'])
        self.assertFalse(run.exists())
        self.assertEqual(39, len([q for q in destination.rglob('*') if q.is_file()]))
        for path in destination.rglob('*'):
            self.assertEqual(0, path.stat().st_mode & 0o222)
        with self.assertRaises(p.FixtureError): p.validate_destination(destination, run)
        self.assertEqual(receipt, json.loads((destination / 'preparation.json').read_text()))

    def test_foreign_operator_and_partial_destinations_rejected(self):
        destination, run = self.layout()
        p.validate_destination(destination, run)
        foreign = destination.parent.parent / 'operator-world' / destination.name
        with self.assertRaises(p.FixtureError): p.validate_destination(foreign, run)
        destination.mkdir(parents=True)
        marker = destination / 'partial-preserved.txt'; marker.write_text('retain original partial')
        with self.assertRaises(p.FixtureError): p.validate_destination(destination, run)
        self.assertEqual('retain original partial', marker.read_text())

    def test_symlink_ancestor_rejected_without_external_writes(self):
        destination, run = self.layout()
        outside = E / (self._testMethodName + '-outside'); outside.mkdir()
        destination.parent.parent.mkdir(parents=True)
        destination.parent.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(p.FixtureError): p.validate_destination(destination, run)
        self.assertFalse(list(outside.iterdir()))

    def test_archive_leak_rejected_not_filtered(self):
        for name, entry in [('ordinary', 'ordinary.txt'), ('fixture', 'data/malum/recipes/control.json')]:
            path = E / (name + '-source-control-only.zip')
            with zipfile.ZipFile(path, 'w') as archive: archive.writestr(entry, b'control only')
            if name == 'ordinary': p.check_archives([path])
            else:
                with self.assertRaises(p.FixtureError): p.check_archives([path])
                with zipfile.ZipFile(path) as archive: self.assertIn(entry, archive.namelist())


if __name__ == '__main__':
    print('Retained source controls, NOT Native:', E, flush=True)
    unittest.main(verbosity=2)
