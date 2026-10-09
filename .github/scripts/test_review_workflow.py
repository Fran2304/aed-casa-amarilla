import os
import re
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
WORKFLOW = ROOT / ".github/workflows/opencode-review.yml"
CONTROL = ROOT / ".github/scripts/review_checkpoint.py"


def run_block(name):
    text = WORKFLOW.read_text()
    start = text.index(f"      - name: {name}")
    end = text.find("\n      - name:", start + 1)
    section = text[start:] if end == -1 else text[start:end]
    match = re.search(r"\n        run: \|\n((?:          .*\n)+)", section)
    if not match:
        raise AssertionError(f"no executable block for {name}")
    return textwrap.dedent(match.group(1))


class WorkflowContractTest(unittest.TestCase):
    def setUp(self):
        self.prepare = run_block("Check successful review checkpoint")
        self.move = run_block("Move trusted checkpoint control outside workspace")
        self.verify = run_block("Verify current head before checkpoint")
        self.record = run_block("Record successful review checkpoint")

    def fake_gh(self, directory):
        gh = Path(directory) / "gh"
        gh.write_text(
            """#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
endpoint = next((arg for arg in args if '/comments' in arg or '/compare/' in arg or '/pulls/' in arg), '')
if '--method' in args and 'POST' in args:
    with open(os.environ['MOCK_POST'], 'w') as stream:
        stream.write('posted')
elif endpoint.endswith('/comments'):
    print(os.environ['MOCK_COMMENTS'])
elif '/compare/' in endpoint:
    print(os.environ.get('MOCK_COMPARE', '{}'))
elif '/pulls/' in endpoint:
    current = json.loads(os.environ['MOCK_CURRENT'])
    if '--jq' in args:
        print(current['head']['sha'] + '\\t' + current['base']['sha'])
    else:
        print(json.dumps(current))
"""
        )
        gh.chmod(gh.stat().st_mode | stat.S_IXUSR)

    def execute(self, block, directory, **extra):
        env = os.environ.copy()
        env.update(
            REPO="example/repo",
            PR_NUMBER="54",
            HEAD_SHA="head",
            BASE_SHA="base",
            GH_TOKEN="token",
            RUNNER_TEMP=directory,
            GITHUB_OUTPUT=str(Path(directory) / "output"),
            MOCK_COMMENTS="[]",
            MOCK_COMPARE="{}",
            MOCK_CURRENT='{"head":{"sha":"head"},"base":{"sha":"base"}}',
            MOCK_POST=str(Path(directory) / "posted"),
            PATH=f"{directory}:{env['PATH']}",
        )
        env.update(extra)
        return subprocess.run(["bash", "-eu", "-c", block], cwd=directory, env=env, text=True, capture_output=True)

    def test_prepare_block_skips_same_head_and_runs_full_without_trusted_script(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.fake_gh(tmp)
            control = Path(tmp) / "opencode-review-control/.github/scripts"
            control.mkdir(parents=True)
            (control / CONTROL.name).write_text(CONTROL.read_text())
            comments = '[{"user":{"login":"github-actions[bot]","type":"Bot"},"body":"<!-- opencode-review-checkpoint:v1 workflow=opencode-review head_sha=head base_sha=base -->"}]'
            result = self.execute(self.prepare, tmp, MOCK_COMMENTS=comments)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("skip=true", Path(tmp, "output").read_text())

            Path(tmp, "opencode-review-control/.github/scripts/review_checkpoint.py").unlink()
            result = self.execute(self.prepare, tmp, MOCK_COMMENTS=comments)
            self.assertEqual(result.returncode, 0, result.stderr)
            output = Path(tmp, "output").read_text()
            self.assertIn("skip=false", output)
            self.assertIn("mode=full", output)

    def test_prepare_block_marks_descendant_incremental_and_force_push_full(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.fake_gh(tmp)
            control = Path(tmp) / "opencode-review-control/.github/scripts"
            control.mkdir(parents=True)
            (control / CONTROL.name).write_text(CONTROL.read_text())
            comments = '[{"user":{"login":"github-actions[bot]","type":"Bot"},"body":"<!-- opencode-review-checkpoint:v1 workflow=opencode-review head_sha=old base_sha=base -->"}]'
            result = self.execute(self.prepare, tmp, MOCK_COMMENTS=comments, MOCK_COMPARE='{"status":"ahead"}')
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("mode=incremental", Path(tmp, "output").read_text())

            result = self.execute(self.prepare, tmp, MOCK_COMMENTS=comments, MOCK_COMPARE='{"status":"diverged"}')
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("mode=full", Path(tmp, "output").read_text())

    def test_stale_head_cannot_record_checkpoint(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.fake_gh(tmp)
            stale = self.execute(
                self.verify,
                tmp,
                MOCK_CURRENT='{"head":{"sha":"other"},"base":{"sha":"base"}}',
            )
            self.assertNotEqual(stale.returncode, 0)
            self.assertFalse(Path(tmp, "posted").exists())

            current = self.execute(self.verify, tmp)
            self.assertEqual(current.returncode, 0, current.stderr)
            recorded = self.execute(self.record, tmp)
            self.assertEqual(recorded.returncode, 0, recorded.stderr)
            self.assertTrue(Path(tmp, "posted").exists())

    def test_move_block_removes_trusted_checkout_before_pr_workspace_is_used(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.fake_gh(tmp)
            source = Path(tmp) / ".review-control/.github/scripts"
            source.mkdir(parents=True)
            (source / CONTROL.name).write_text(CONTROL.read_text())
            moved = self.execute(self.move, tmp)
            self.assertEqual(moved.returncode, 0, moved.stderr)
            trusted = Path(tmp, "opencode-review-control/.github/scripts/review_checkpoint.py")
            self.assertTrue(trusted.exists())
            self.assertFalse(Path(tmp, ".review-control").exists())

            malicious = Path(tmp, ".review-control/.github/scripts")
            malicious.mkdir(parents=True)
            (malicious / CONTROL.name).write_text("raise RuntimeError('PR code')")
            comments = '[{"user":{"login":"github-actions[bot]","type":"Bot"},"body":"<!-- opencode-review-checkpoint:v1 workflow=opencode-review head_sha=head base_sha=base -->"}]'
            result = self.execute(self.prepare, tmp, MOCK_COMMENTS=comments)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("skip=true", Path(tmp, "output").read_text())

    def test_workflow_uses_trusted_checkout_and_success_conditions(self):
        text = WORKFLOW.read_text()
        self.assertLess(text.index("path: .review-control"), text.index("- name: Checkout PR"))
        self.assertIn('mv .review-control "$RUNNER_TEMP/trusted-review-checkout"', text)
        self.assertIn('control_script="$RUNNER_TEMP/opencode-review-control/.github/scripts/review_checkpoint.py"', text)
        action_condition = re.search(r"- name: Run OpenCode review\n        if: (.+)", text)
        self.assertEqual(
            action_condition.group(1) if action_condition else None,
            "steps.prepare.outputs.skip != 'true'",
        )
        for step in ("Verify current head before checkpoint", "Record successful review checkpoint"):
            section = text[text.index(f"- name: {step}"):]
            condition_match = re.search(r"\n        if: (.+)", section)
            condition = condition_match.group(1) if condition_match else None
            self.assertEqual(condition, "success() && steps.prepare.outputs.skip != 'true'")
        self.assertNotIn("python3 .github/scripts/review_checkpoint.py", text)


if __name__ == "__main__":
    unittest.main()
