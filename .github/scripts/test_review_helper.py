import argparse
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import review_helper as helper


def ns(**values):
    return argparse.Namespace(**values)


class ReviewHelperTest(unittest.TestCase):
    def test_changed_lines_resets_files_deleted_and_quoted_rename(self):
        diff = '''diff --git a/a.txt b/a.txt
--- a/a.txt
+++ b/a.txt
@@ -1,4 +1,5 @@
 old
+new
diff --git a/z.txt b/z.txt
--- a/z.txt
+++ /dev/null
@@ -1,2 +0,0 @@
-gone
-gone too
diff --git "a/old name.txt" "b/new name.txt"
--- "a/old name.txt"
+++ "b/new name.txt"
@@ -1,1 +1,2 @@
 old
+renamed
'''
        self.assertEqual(helper.changed_lines(diff), {"a.txt": {2}, "new name.txt": {2}})

    def test_part_text_event_and_malformed_schema(self):
        event = {"type": "text", "part": {"type": "text", "text":
            '{"schema":"opencode-findings/v1","findings":[]}'}}
        self.assertEqual(helper.extract_json(json.dumps(event))["findings"], [])
        with self.assertRaises(ValueError):
            helper.extract_json('{"schema":"opencode-findings/v1","findings":[{"path":"x","body":"bad","severity":"blocker"}]}')

    def test_prepare_incremental_scope_and_force_push_fallback(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            helper.run_git("init", cwd=str(repo))
            (repo / "file.txt").write_text("one\n")
            helper.run_git("add", ".", cwd=str(repo)); helper.run_git("-c", "user.email=a@b", "-c", "user.name=a", "commit", "-m", "one", cwd=str(repo))
            base = helper.run_git("rev-parse", "HEAD", cwd=str(repo)).strip()
            (repo / "file.txt").write_text("one\ntwo\n")
            helper.run_git("add", ".", cwd=str(repo)); helper.run_git("-c", "user.email=a@b", "-c", "user.name=a", "commit", "-m", "two", cwd=str(repo))
            head = helper.run_git("rev-parse", "HEAD", cwd=str(repo)).strip()
            prompt = repo / "prompt"; prompt.write_text("rules")
            output, scope = repo / "input.json", repo / "scope.json"
            common = dict(repo=str(repo), base=base, head=head, previous_base=base,
                          prompt=str(prompt), output=str(output), scope=str(scope))
            helper.prepare(ns(**common, last_sha=base))
            payload = json.loads(output.read_text()); saved_scope = json.loads(scope.read_text())
            self.assertTrue(payload["incremental"]); self.assertEqual(saved_scope["start_sha"], base)
            helper.prepare(ns(**common, last_sha="not-an-ancestor"))
            self.assertFalse(json.loads(scope.read_text())["incremental"])
            self.assertEqual(json.loads(output.read_text())["range"], f"{base}...{head}")
            divergent = dict(common, previous_base="different-base")
            helper.prepare(ns(**divergent, last_sha=base))
            self.assertFalse(json.loads(scope.read_text())["incremental"])

    def test_prepare_includes_trusted_domain_and_head_context(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); repo = root / "head"; repo.mkdir()
            helper.run_git("init", cwd=str(repo)); (repo / "A.java").write_text("class A {}\n")
            helper.run_git("add", ".", cwd=str(repo)); helper.run_git("-c", "user.email=a@b", "-c", "user.name=a", "commit", "-m", "a", cwd=str(repo))
            base = helper.run_git("rev-parse", "HEAD", cwd=str(repo)).strip()
            (repo / "A.java").write_text("class A { int changed = 1; }\n")
            helper.run_git("add", ".", cwd=str(repo)); helper.run_git("-c", "user.email=a@b", "-c", "user.name=a", "commit", "-m", "changed", cwd=str(repo))
            sha = helper.run_git("rev-parse", "HEAD", cwd=str(repo)).strip()
            (root / "CONTEXT.md").write_text("Alumno")
            (root / "docs").mkdir(); (root / "docs/flujo_matricula_casa_amarilla_2027_ia.md").write_text("48 h")
            (root / "docs/requerimientos_proyecto.md").write_text("ArrayList")
            prompt = root / "prompt"; prompt.write_text("rules")
            output, scope = root / "input", root / "scope"
            helper.prepare(ns(repo=str(repo), base=base, head=sha, last_sha="", previous_base="",
                              prompt=str(prompt), output=str(output), scope=str(scope), context_root=str(root)))
            model_prompt = (root / "model_prompt.md").read_text()
            self.assertIn("Alumno", model_prompt); self.assertIn("48 h", model_prompt); self.assertIn("class A { int changed", model_prompt)

    def test_checkpoint_authentication_and_pagination(self):
        reviews = [{"user": {"login": "evil-bot"}, "body": "<!-- opencode-review:v1 commit_id=bad base_sha=x -->"},
                   {"user": {"login": "github-actions[bot]"}, "body": "<!-- opencode-review:v1 commit_id=head base_sha=base -->"}]
        self.assertEqual(helper.checkpoint(reviews), {"head_sha": "head", "base_sha": "base"})
        gh = helper.GitHub("t", "r")
        with patch.object(gh, "request", side_effect=[[{"n": i} for i in range(100)], [{"n": 100}]]):
            self.assertEqual(len(gh.all("reviews")), 101)

    def test_publish_inline_and_checkpoint_body_deduplicate(self):
        finding = {"id": "stable-bug", "path": "src/A.java", "line": 2,
                   "severity": "blocker", "body": "broken rule"}
        with tempfile.TemporaryDirectory() as tmp:
            findings = Path(tmp) / "findings.json"; findings.write_text(json.dumps({"schema": "opencode-findings/v1", "findings": [finding]}))
            scope = Path(tmp) / "scope.json"; scope.write_text(json.dumps({"schema": "opencode-review-scope/v1", "base_sha": "base", "head_sha": "head", "start_sha": "base", "incremental": False, "new_lines": {"src/A.java": [2]}, "full_lines": {"src/A.java": [2]}}))
            args = ns(findings=str(findings), scope=str(scope), token="t", repo="r", number="1", head="head", base="base")
            responses = [{"head": {"sha": "head"}, "base": {"sha": "base"}}, [], [{"filename": "src/A.java", "patch": "@@ -1,1 +1,2 @@\n old\n+new\n"}], [], None]
            with patch.object(helper.GitHub, "request", side_effect=responses) as request:
                helper.publish(args)
            post = request.call_args.args[2]
            self.assertEqual(post["event"], "REQUEST_CHANGES"); self.assertEqual(post["comments"][0]["line"], 2)
            duplicate_scope = scope
            responses = [{"head": {"sha": "head"}, "base": {"sha": "base"}}, [{"user": {"login": "github-actions[bot]"}, "body": "<!-- opencode-findings:v1 ids=stable-bug -->"}], [{"filename": "src/A.java", "patch": "@@ -1,1 +1,2 @@\n old\n+new\n"}], [], None]
            with patch.object(helper.GitHub, "request", side_effect=responses) as request:
                helper.publish(args)
            self.assertEqual(request.call_args.args[2]["event"], "COMMENT")

    def test_previous_line_is_not_inline_and_stale_head_fails(self):
        finding = {"id": "old", "path": "src/A.java", "line": 2, "severity": "blocker", "body": "old"}
        with tempfile.TemporaryDirectory() as tmp:
            f, s = Path(tmp) / "f", Path(tmp) / "s"
            f.write_text(json.dumps({"schema": "opencode-findings/v1", "findings": [finding]}))
            s.write_text(json.dumps({"schema": "opencode-review-scope/v1", "base_sha": "base", "head_sha": "head", "start_sha": "last", "incremental": True, "new_lines": {"src/A.java": [3]}, "full_lines": {"src/A.java": [2, 3]}}))
            args = ns(findings=str(f), scope=str(s), token="t", repo="r", number="1", head="head", base="base")
            responses = [{"head": {"sha": "old-head"}, "base": {"sha": "base"}}]
            with patch.object(helper.GitHub, "request", side_effect=responses):
                with self.assertRaises(RuntimeError): helper.publish(args)

    def test_incremental_publisher_intersects_full_scope_and_rejects_old_line(self):
        base_finding = {"id": "old", "path": "src/A.java", "line": 2, "severity": "blocker", "body": "old"}
        new_finding = {"id": "new", "path": "src/A.java", "line": 3, "severity": "blocker", "body": "new"}
        with tempfile.TemporaryDirectory() as tmp:
            findings = Path(tmp) / "findings.json"
            scope = Path(tmp) / "scope.json"
            scope.write_text(json.dumps({"schema": "opencode-review-scope/v1", "base_sha": "base", "head_sha": "head", "start_sha": "last", "incremental": True, "new_lines": {"src/A.java": [3]}, "full_lines": {"src/A.java": [2, 3]}}))
            args = ns(findings=str(findings), scope=str(scope), token="t", repo="r", number="1", head="head", base="base")
            common = [{"head": {"sha": "head"}, "base": {"sha": "base"}}, [{"user": {"login": "github-actions[bot]"}, "body": "<!-- opencode-review:v1 commit_id=last base_sha=base -->"}], {"status": "ahead", "files": [{"filename": "src/A.java", "patch": "@@ -2,0 +3,1 @@\n+new\n"}]}, [{"filename": "src/A.java", "patch": "@@ -1,0 +2,2 @@\n+old-change\n+new\n"}], []]
            findings.write_text(json.dumps({"schema": "opencode-findings/v1", "findings": [new_finding]}))
            with patch.object(helper.GitHub, "request", side_effect=common + [None]) as request:
                helper.publish(args)
            self.assertEqual(request.call_args.args[2]["comments"][0]["line"], 3)
            findings.write_text(json.dumps({"schema": "opencode-findings/v1", "findings": [base_finding]}))
            with patch.object(helper.GitHub, "request", side_effect=common) as request:
                with self.assertRaisesRegex(ValueError, "outside trusted incremental/full diff scope"):
                    helper.publish(args)
            self.assertEqual(request.call_count, 5)

    def test_integrated_restore_line_normalizes_to_clean_checkpoint(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); repo = root / "repo"; repo.mkdir()
            helper.run_git("init", cwd=str(repo))
            file = repo / "A.java"; file.write_text("base1\nbase2\n")
            helper.run_git("add", ".", cwd=str(repo)); helper.run_git("-c", "user.email=a@b", "-c", "user.name=a", "commit", "-m", "base", cwd=str(repo))
            base = helper.run_git("rev-parse", "HEAD", cwd=str(repo)).strip()
            file.write_text("changed1\nchanged2\n")
            helper.run_git("add", ".", cwd=str(repo)); helper.run_git("-c", "user.email=a@b", "-c", "user.name=a", "commit", "-m", "first", cwd=str(repo))
            last = helper.run_git("rev-parse", "HEAD", cwd=str(repo)).strip()
            file.write_text("base1\nchanged2\n")
            helper.run_git("add", ".", cwd=str(repo)); helper.run_git("-c", "user.email=a@b", "-c", "user.name=a", "commit", "-m", "restore", cwd=str(repo))
            head = helper.run_git("rev-parse", "HEAD", cwd=str(repo)).strip()
            prompt = root / "prompt"; prompt.write_text("rules")
            output, scope = root / "input", root / "scope"
            helper.prepare(ns(repo=str(repo), base=base, head=head, last_sha=last, previous_base=base,
                              prompt=str(prompt), output=str(output), scope=str(scope), context_root=str(root)))
            prepared_scope = json.loads(scope.read_text())
            self.assertEqual(prepared_scope["full_lines"], {"A.java": [2]})
            self.assertEqual(prepared_scope["new_lines"], {})
            findings = root / "findings"; findings.write_text(json.dumps({"schema": "opencode-findings/v1", "findings": []}))
            args = ns(findings=str(findings), scope=str(scope), token="t", repo="r", number="1", head=head, base=base)
            responses = [
                {"head": {"sha": head}, "base": {"sha": base}},
                [{"user": {"login": "github-actions[bot]"}, "body": f"<!-- opencode-review:v1 commit_id={last} base_sha={base} -->"}],
                {"status": "ahead", "files": [{"filename": "A.java", "patch": "@@ -1,1 +1,1 @@\n-changed1\n+base1\n"}]},
                [{"filename": "A.java", "patch": "@@ -1,2 +1,2 @@\n base1\n-base2\n+changed2\n"}],
                [], None,
            ]
            with patch.object(helper.GitHub, "request", side_effect=responses) as request:
                helper.publish(args)
            post = request.call_args.args[2]
            self.assertEqual(post["event"], "COMMENT")
            self.assertIn("sin hallazgos nuevos", post["body"])

    def test_workflow_security_contract(self):
        workflow = Path(__file__).parent.parent / "workflows" / "opencode-review.yml"
        text = workflow.read_text()
        self.assertIn("pull_request_target", text)
        self.assertIn('OPENCODE_PERMISSION: \'{"*":"deny"}\'', text)
        self.assertIn("OPENCODE_DISABLE_DEFAULT_PLUGINS", text)
        self.assertNotIn("FIGMA_TOKEN", text)
        self.assertIn("--scope review-scope.json", text)


if __name__ == "__main__":
    unittest.main()
