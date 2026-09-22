import importlib.util
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('deploy', Path(__file__).with_name('deploy.py'))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class DeploymentGuardsTest(unittest.TestCase):
    def test_production_context_requires_explicit_matching_branch_and_sha(self):
        env = {'JOB_NAME': 'gongguhan-DevelopV2', 'GIT_BRANCH': 'origin/DevelopV2'}
        deploy.verify_context('a'*40, env, 'a'*40)
        for invalid in ({}, {**env, 'GIT_BRANCH': ''}, {**env, 'GIT_BRANCH': 'origin/DevelopV1'},
                        {**env, 'JOB_NAME': 'gongguhan-ci-validation'}):
            with self.assertRaises(RuntimeError): deploy.verify_context('a'*40, invalid, 'a'*40)
        with self.assertRaises(RuntimeError): deploy.verify_context('a'*40, env, 'b'*40)

    def test_healthy_release_activates_without_rollback(self):
        with patch.object(deploy, 'compose') as compose, patch.object(deploy, 'wait_healthy'), patch.object(deploy, 'verify_site') as verify:
            deploy.activate({'sha': 'new'}, {'sha': 'old'})
            self.assertEqual([c.args[0]['sha'] for c in compose.call_args_list], ['new', 'new'])
            verify.assert_called_once()

    def test_backend_failure_rolls_back_without_switching_new_nginx(self):
        with patch.object(deploy, 'compose') as compose, patch.object(deploy, 'wait_healthy', side_effect=[RuntimeError('unhealthy'), None, None]), patch.object(deploy, 'verify_site'):
            with self.assertRaisesRegex(RuntimeError, 'unhealthy'):
                deploy.activate({'sha': 'new'}, {'sha': 'old'})
            self.assertEqual([c.args[0]['sha'] for c in compose.call_args_list], ['new', 'old', 'old'])
            self.assertNotIn('nginx', compose.call_args_list[0].args)

    def test_public_health_failure_rolls_back_and_still_fails_build(self):
        with patch.object(deploy, 'compose') as compose, patch.object(deploy, 'wait_healthy'), patch.object(deploy, 'verify_site', side_effect=[RuntimeError('public health'), None]):
            with self.assertRaisesRegex(RuntimeError, 'public health'):
                deploy.activate({'sha': 'new'}, {'sha': 'old'})
            self.assertEqual([c.args[0]['sha'] for c in compose.call_args_list], ['new', 'new', 'old', 'old'])

    def test_failed_rollback_is_not_hidden(self):
        with patch.object(deploy, 'compose', side_effect=[RuntimeError('candidate'), RuntimeError('rollback')]), patch.object(deploy, 'wait_healthy'), patch.object(deploy, 'verify_site'):
            with self.assertRaisesRegex(RuntimeError, 'rollback'):
                deploy.activate({'sha': 'new'}, {'sha': 'old'})

    def test_full_sha_only(self):
        for value in ('main','DevelopV2','../escape','a'*39,'a'*41,'A'*40,'; whoami'):
            with self.assertRaises(ValueError): deploy.validate_sha(value)
        deploy.validate_sha('a'*40)

    def test_images_are_commit_specific(self):
        images=deploy.image_map('b'*40)
        self.assertEqual(set(images),{'backend','ai','nginx'})
        self.assertTrue(all(i.endswith(':'+'b'*40) for i in images.values()))

    def test_migration_requires_exact_reviewed_content(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            migration=root/'migration.sql'
            migration.write_text('ALTER TABLE test ADD COLUMN sample INT NULL;')
            with self.assertRaises(RuntimeError): deploy.verify_migrations(['migration.sql'],{},root)
            approved={'migration.sql':hashlib.sha256(migration.read_bytes()).hexdigest()}
            deploy.verify_migrations(['migration.sql'],approved,root)
            migration.write_text('DROP TABLE test;')
            with self.assertRaises(RuntimeError): deploy.verify_migrations(['migration.sql'],approved,root)
            migration.unlink()
            with self.assertRaises(RuntimeError): deploy.verify_migrations(['migration.sql'],approved,root)


if __name__=='__main__': unittest.main()
