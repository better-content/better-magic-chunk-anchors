#!/usr/bin/env python3
"""Synthetic role/byte-binding negatives, not SDK loading or Root qualification."""
import copy
import json
import os
from pathlib import Path
import unittest
import uuid
import zipfile
import canonical_sdk_roles as roles

class RoleControls(unittest.TestCase):
    def setUp(self):
        base = Path(os.environ.get('BC_SDK_ROLE_TEST_OUTPUT', str(Path(__file__).resolve().parents[1]/'build/sdk-role-controls')))
        self.directory = base/str(uuid.uuid4())
        self.staging = self.directory/'staged-jars'
        self.staging.mkdir(parents=True)
        self.peer = dict(coordinate='custom.local:peer:1', role='canonical_producer', repository='peer', mod_id='peer', artifact='peer.jar')
        self.spec = dict(schema='bc.canonical_sdk_roles.v1', roles=[dict(coordinate='original:api:1',role='upstream_api'),self.peer])
        metadata = ('schema=bc.custom_mod_source.v1\nrepository=peer\nmod_id=peer\ncommit='+'a'*40+'\n').encode()
        path = self.staging/'peer.jar'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('META-INF/better-content-source.properties', metadata)
            archive.writestr('data/peer/tags/blocks/bounded.json', '{}')
        self.binding = dict(actual_declared_sdk_coordinates=['custom.local:peer:1','original:api:1'],
            actual_declared_original_coordinates=['original:api:1'], canonical_staging=str(self.staging),
            declared_sdk_configuration_roles=[dict(configuration='nativeFixtureRuntimeOnly',coordinate='custom.local:peer:1'),dict(configuration='implementation',coordinate='original:api:1')],
            canonical_providers=[dict(self.peer,path=str(path),sha256=roles.sha(path.read_bytes()),source_metadata_sha256=roles.sha(metadata),commit='a'*40)])
    def test_source_role_extraction_includes_inherited_modules_and_files(self):
        source = (Path(__file__).resolve().parents[1]/'gradle/native-sdk-roles.gradle').read_text()
        self.assertEqual(2, source.count('allDependencies.findAll'))
        self.assertNotIn('.dependencies.findAll', source)
    def test_source_receipt_creates_ordinary_directory_before_exclusive_write(self):
        source = (Path(__file__).resolve().parents[1]/'gradle/native-canonical-providers.gradle').read_text()
        self.assertLess(source.index('Files.isSymbolicLink(part)', source.index('def output = receipt.toPath()')), source.index('Files.createDirectories(output.parent)'))
        self.assertLess(source.index('Files.createDirectories(output.parent)'), source.index('Files.write(output, bytes, StandardOpenOption.CREATE_NEW)'))
    def test_source_unit_projection_cannot_be_native_qualification(self):
        source = (Path(__file__).resolve().parents[1]/'gradle/native-glm.gradle').read_text()
        native = source.split('project.ext.bcAnchorsGlmBindings = {', 1)[1].split('\ntasks.named', 1)[0]
        self.assertLess(native.index('bcCanonicalSdkRoles()'), native.index('projectionBindings()'))
        self.assertIn('sdkRoles + projectionBindings()', native)
        unit = source.split("tasks.register('verifyNativeGlmSourceControls')", 1)[1]
        self.assertIn('JsonOutput.toJson(projectionBindings())', unit)
        self.assertNotIn('source_control_only', native)
    def test_positive_dynamic_and_content_disclosure(self):
        result = roles.validate(self.binding,self.spec)
        self.assertEqual(2,len(result['roles']))
        self.assertIn('data/peer/tags/blocks/bounded.json',result['roles'][1]['runtime_data_resources'])
        self.assertNotIn('PASSED_OWNING_GATES',json.dumps(result))
    def test_source_control_and_release_staging_both_supported(self):
        self.staging.rename(self.directory/'source-jars')
        self.binding['canonical_staging']=str(self.directory/'source-jars')
        self.binding['canonical_providers'][0]['path']=str(self.directory/'source-jars/peer.jar')
        roles.validate(self.binding,self.spec)
    def test_extra(self):
        self.binding['actual_declared_sdk_coordinates'].append('extra:unknown:1')
        with self.assertRaisesRegex(ValueError,'missing.*extra|extra.*missing'): roles.validate(self.binding,self.spec)
    def test_missing(self):
        self.binding['actual_declared_sdk_coordinates'].pop()
        with self.assertRaises(ValueError): roles.validate(self.binding,self.spec)
    def test_duplicate(self):
        self.binding['actual_declared_sdk_coordinates'].append('original:api:1')
        with self.assertRaises(ValueError): roles.validate(self.binding,self.spec)
    def test_configuration_duplicate(self):
        self.binding['declared_sdk_configuration_roles'].append(self.binding['declared_sdk_configuration_roles'][0])
        with self.assertRaises(ValueError): roles.validate(self.binding,self.spec)
    def test_unknown_file_dependency(self):
        self.binding['actual_declared_sdk_files']=['runtimeOnly|unexpected.jar']
        with self.assertRaisesRegex(ValueError,'file dependency'): roles.validate(self.binding,self.spec)
    def test_dynamic_hash(self):
        self.binding['canonical_providers'][0]['sha256']='0'*64
        with self.assertRaisesRegex(ValueError,'digest'): roles.validate(self.binding,self.spec)
    def test_source_commit(self):
        self.binding['canonical_providers'][0]['commit']='b'*40
        with self.assertRaisesRegex(ValueError,'commit'): roles.validate(self.binding,self.spec)
    def test_bad_role(self):
        self.binding['canonical_providers'][0]['role']='upstream_original'
        with self.assertRaisesRegex(ValueError,'role'): roles.validate(self.binding,self.spec)
    def test_metadata_digest(self):
        self.binding['canonical_providers'][0]['source_metadata_sha256']='0'*64
        with self.assertRaises(ValueError): roles.validate(self.binding,self.spec)
    def test_stage_escape(self):
        self.binding['canonical_staging']=str(self.directory)
        with self.assertRaises(ValueError): roles.validate(self.binding,self.spec)
    def test_extra_peer_glm_rejected_not_filtered(self):
        peer=self.binding['canonical_providers'][0]
        with zipfile.ZipFile(peer['path'],'a') as archive:
            archive.writestr('data/forge/loot_modifiers/global_loot_modifiers.json','{"entries":["peer:new"]}')
        peer['sha256']=roles.sha(Path(peer['path']).read_bytes())
        with self.assertRaisesRegex(ValueError,'GLM content'): roles.validate(self.binding,self.spec)

if __name__=='__main__': unittest.main()
