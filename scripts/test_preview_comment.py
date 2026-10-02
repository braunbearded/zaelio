import json
from pathlib import Path
import subprocess
import textwrap
import unittest


WORKFLOW = (Path(__file__).parent.parent / '.github/workflows/tests.yml').read_text()
COMMENT_SCRIPT = textwrap.dedent(WORKFLOW.split('          script: |\n', 1)[1])
MARKER = '<!-- zaelio-preview-apk -->'


class PreviewCommentTest(unittest.TestCase):
    def run_comment(self, changelog, comments=()):
        fixture = json.dumps({'changelog': changelog, 'comments': comments})
        runner = f'''
            const fixture = {fixture};
            const calls = [];
            process.env.RELEASE_VERSION = '1.2.3';
            process.env.BUILD_SHA = 'built-commit';
            process.env.APK_URL = 'https://example.com/apk';
            const context = {{repo: {{owner: 'owner', repo: 'zaelio'}},
                payload: {{pull_request: {{number: 25, head: {{sha: 'newer-commit'}}}}}}}};
            const github = {{rest: {{
                repos: {{getContent: async args => {{
                    calls.push({{method: 'getContent', args}});
                    return {{data: {{content: Buffer.from(fixture.changelog).toString('base64')}}}};
                }}}},
                issues: {{listComments: () => {{}},
                    createComment: async args => calls.push({{method: 'createComment', args}}),
                    updateComment: async args => calls.push({{method: 'updateComment', args}})}}
            }}, paginate: async () => fixture.comments}};
            const AsyncFunction = Object.getPrototypeOf(async function () {{}}).constructor;
            (async () => {{
                try {{
                    await new AsyncFunction('github', 'context', {json.dumps(COMMENT_SCRIPT)})(github, context);
                    console.log(JSON.stringify({{calls}}));
                }} catch (error) {{
                    console.log(JSON.stringify({{calls, error: error.message}}));
                }}
            }})();
        '''
        result = subprocess.run(['node', '-e', runner], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads(result.stdout)

    def test_comment_uses_validated_version_and_never_checks_out_pr_code(self):
        self.assertIn('version: ${{ steps.source.outputs.version }}', WORKFLOW)
        self.assertIn('python3 scripts/release_ci.py "$VERSION_BRANCH" >> "$GITHUB_OUTPUT"', WORKFLOW)
        comment_job = WORKFLOW.split('\n  comment:', 1)[1]
        self.assertIn('RELEASE_VERSION: ${{ needs.build.outputs.version }}', comment_job)
        self.assertNotIn('actions/checkout', comment_job)
        self.assertIn('pull-requests: write', comment_job)
        self.assertNotIn('pull-requests: write', WORKFLOW.split('\n  comment:', 1)[0])

    def test_creates_and_updates_comment_with_only_matching_version_notes(self):
        notes = '- Änderung mit Umlaut.\n\n### Details\n- `code` und ${notExecuted}.'
        changelog = ('# Changelog\n\n## Unreleased\n\n- Future changes.\n\n'
                     '## 1.2.30\n\n- Wrong version.\n\n'
                     f'## 1.2.3\n\n{notes}\n\n## 1.2.2\n\n- Old changes.\n')
        for existing in (False, True):
            for newline in ('\n', '\r\n'):
                with self.subTest(existing=existing, newline=newline):
                    comments = [{'id': 1, 'user': {'login': 'human'}, 'body': MARKER}]
                    if existing:
                        comments.append({'id': 2, 'user': {'login': 'github-actions[bot]'},
                                         'body': MARKER + '\nOld preview'})
                    result = self.run_comment(changelog.replace('\n', newline), comments)
                    self.assertNotIn('error', result)
                    fetch, comment = result['calls']
                    self.assertEqual({'owner': 'owner', 'repo': 'zaelio', 'path': 'CHANGELOG.md',
                                      'ref': 'built-commit'}, fetch['args'])
                    self.assertEqual('updateComment' if existing else 'createComment', comment['method'])
                    self.assertEqual(2 if existing else 25,
                                     comment['args']['comment_id' if existing else 'issue_number'])
                    body = comment['args']['body']
                    self.assertTrue(body.startswith(MARKER))
                    self.assertIn('[APK herunterladen](https://example.com/apk)', body)
                    self.assertIn('Commit: `built-commit`', body)
                    self.assertTrue(body.endswith('### Changelog 1.2.3\n\n' + notes))
                    for other in ('Future changes.', 'Wrong version.', 'Old changes.'):
                        self.assertNotIn(other, body)

    def test_final_section_without_trailing_newline_is_included(self):
        result = self.run_comment('# Changelog\n\n## 1.2.3\n\n- Final notes.')
        self.assertNotIn('error', result)
        self.assertTrue(result['calls'][-1]['args']['body'].endswith('- Final notes.'))

    def test_missing_or_empty_version_notes_fail_without_posting_comment(self):
        for changelog in ('## 1.2.30\n\n- Not this version.',
                          '## 1.2.3\n \n## 1.2.2\n\n- Old notes.'):
            with self.subTest(changelog=changelog):
                result = self.run_comment(changelog)
                self.assertEqual('Missing changelog for version 1.2.3', result['error'])
                self.assertEqual(['getContent'], [call['method'] for call in result['calls']])


if __name__ == '__main__':
    unittest.main()
