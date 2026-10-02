import shutil
import subprocess
import tempfile
from pathlib import Path
import unittest

from release_ci import release_info, update_metadata


class ReleaseCiTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'app').mkdir()
        (self.root / 'app/build.gradle').write_text("versionName = '1.2.3'\nversionCode = 4\n")
        self.notes = self.root / 'fastlane/metadata/android/en-US/changelogs/4.txt'
        self.notes.parent.mkdir(parents=True)
        self.notes.write_text('Fix saved values.\n')
        self.metadata = self.root / 'docs/fdroiddata/com.zaelio.app.yml'
        self.metadata.parent.mkdir(parents=True)
        self.metadata.write_text(
            'Builds:\n  - versionName: 1.2.2\n    versionCode: 3\n    commit: ' + 'a' * 40 + '\n'
            'Binaries: https://example.com/v%v/zaelio.apk\nAllowedAPKSigningKeys: trusted-key\n'
            'CurrentVersion: 1.2.2\nCurrentVersionCode: 3\n')

    def test_preview_only_runs_for_same_repository_version_prs_and_updates(self):
        workflow = (Path(__file__).parent.parent / '.github/workflows/tests.yml').read_text()
        self.assertNotIn('\n  push:', workflow)
        self.assertNotIn('pull_request_target:', workflow)
        self.assertIn('    branches: [main]', workflow)
        self.assertIn('    types: [opened, reopened, synchronize]', workflow)
        self.assertIn('github.event.pull_request.head.repo.full_name == github.repository', workflow)
        self.assertIn("startsWith(github.head_ref, 'v')", workflow)

    def test_release_uses_main_context_and_only_the_trusted_merged_commit(self):
        workflow = (Path(__file__).parent.parent / '.github/workflows/release.yml').read_text()
        self.assertIn('  pull_request_target:\n    branches: [main]\n    types: [closed]', workflow)
        self.assertIn("github.ref == 'refs/heads/main'", workflow)
        self.assertIn("github.event.pull_request.base.ref == 'main'", workflow)
        self.assertIn('github.event.pull_request.merged == true', workflow)
        self.assertIn('github.event.pull_request.head.repo.full_name == github.repository', workflow)
        self.assertIn("startsWith(github.event.pull_request.head.ref, 'v')", workflow)
        self.assertIn('ref: ${{ github.event.pull_request.merge_commit_sha }}', workflow)
        self.assertNotIn('ref: ${{ github.event.pull_request.head.sha }}', workflow)
        self.assertLess(workflow.index('git merge-base --is-ancestor "$RELEASE_SHA" origin/main'),
                        workflow.index('python3 scripts/release_ci.py'))

    def test_version_branch_must_match_prepared_version(self):
        for branch in ('v1.2.3', 'v1.2.3-fix', 'v1.2.3/feature'):
            self.assertEqual(('1.2.3', 4), release_info(self.root, branch))
        for branch in ('main', 'verbose', 'v1.2', 'v1.2.30', 'v1.2.4', 'v1.2.3fix'):
            with self.subTest(branch=branch), self.assertRaises(ValueError):
                release_info(self.root, branch)

    def test_fastlane_notes_must_exist_and_fit_limit(self):
        for notes in ('', ' ' * 10, 'a' * 501):
            self.notes.write_text(notes)
            with self.assertRaises(ValueError):
                release_info(self.root, 'v1.2.3')
        self.notes.unlink()
        with self.assertRaises(FileNotFoundError):
            release_info(self.root, 'v1.2.3')

    def test_metadata_uses_full_merge_hash_and_preserves_signing_settings(self):
        update_metadata(self.metadata, '1.2.3', 4, 'b' * 40)
        text = self.metadata.read_text()
        for expected in ('- versionName: 1.2.3', 'versionCode: 4', 'commit: ' + 'b' * 40,
                         'CurrentVersion: 1.2.3', 'CurrentVersionCode: 4',
                         'Binaries: https://example.com/v%v/zaelio.apk',
                         'AllowedAPKSigningKeys: trusted-key'):
            self.assertIn(expected, text)
        update_metadata(self.metadata, '1.2.3', 4, 'b' * 40)
        self.assertEqual(text, self.metadata.read_text())

    def test_invalid_metadata_or_short_hash_does_not_partially_write(self):
        before = self.metadata.read_text()
        with self.assertRaises(ValueError):
            update_metadata(self.metadata, '1.2.3', 4, 'abc123')
        self.assertEqual(before, self.metadata.read_text())
        self.metadata.write_text(before.replace('CurrentVersionCode: 3\n', ''))
        incomplete = self.metadata.read_text()
        with self.assertRaises(ValueError):
            update_metadata(self.metadata, '1.2.3', 4, 'b' * 40)
        self.assertEqual(incomplete, self.metadata.read_text())

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True, stderr=subprocess.STDOUT).strip()

    def prepare_repo(self):
        scripts = self.root / 'scripts'
        scripts.mkdir()
        for name in ('release.sh', 'release_ci.py'):
            shutil.copy(Path(__file__).parent / name, scripts / name)
        (self.root / 'CHANGELOG.md').write_text('# Changelog\n\n## Unreleased\n\n- Keep these details.\n\n## 1.2.3\n\n- Old notes.\n')
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'test@example.com')
        self.git('add', '.')
        self.git('commit', '-m', 'Initial')
        self.git('switch', '-c', 'v1.2.4')

    def run_script(self, answers):
        return subprocess.run(['bash', 'scripts/release.sh'], input=answers, cwd=self.root,
                              text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    def test_local_preparation_commits_branch_but_not_tag_or_metadata(self):
        self.prepare_repo()
        before_metadata = self.metadata.read_text()
        result = self.run_script('1.2.4\n5\nShort release notes.\n\nn\nn\ny\nn\n')
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertEqual('v1.2.4', self.git('branch', '--show-current'))
        self.assertEqual('', self.git('tag', '--list'))
        self.assertEqual('Prepare release 1.2.4', self.git('log', '-1', '--format=%s'))
        self.assertEqual(before_metadata, self.metadata.read_text())
        self.assertEqual(('1.2.4', 5), release_info(self.root, 'v1.2.4'))
        changelog = (self.root / 'CHANGELOG.md').read_text()
        self.assertIn('## Unreleased\n\n## 1.2.4\n\n- Keep these details.', changelog)
        self.assertIn('## 1.2.3\n\n- Old notes.', changelog)
        self.assertNotIn('git push origin refs/tags/', result.stdout)
        self.git('tag', 'v1.2.4')
        result = self.run_script('1.2.4\n5\n')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Tag v1.2.4 already exists', result.stdout)
        self.assertEqual(changelog, (self.root / 'CHANGELOG.md').read_text())

    def test_oversized_release_notes_leave_version_files_unchanged(self):
        self.prepare_repo()
        config = (self.root / 'app/build.gradle').read_text()
        changelog = (self.root / 'CHANGELOG.md').read_text()
        result = self.run_script('1.2.4\n5\n' + 'a' * 501 + '\n\n')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(config, (self.root / 'app/build.gradle').read_text())
        self.assertEqual(changelog, (self.root / 'CHANGELOG.md').read_text())
        self.assertFalse((self.notes.parent / '5.txt').exists())


if __name__ == '__main__':
    unittest.main()
