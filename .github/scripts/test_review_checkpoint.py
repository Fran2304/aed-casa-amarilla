import json
import unittest

from review_checkpoint import decision, latest_for_base


def comment(head, base, login="github-actions[bot]", author_type="Bot", lqtm=False, body=None):
    marker = f"<!-- opencode-review-checkpoint:v1 workflow=opencode-review head_sha={head} base_sha={base} -->"
    return {
        "body": body if body is not None else (f"LQTM {marker}" if lqtm else marker),
        "user": {"login": login, "type": author_type},
    }


class CheckpointTest(unittest.TestCase):
    def test_same_head_skips_only_trusted_checkpoint(self):
        self.assertEqual(decision([comment("head", "base")], "head", "base", {}), ("skip", "head"))

    def test_new_descendant_is_incremental(self):
        self.assertEqual(
            decision([comment("old", "base")], "new", "base", {"status": "ahead"}),
            ("incremental", "old"),
        )

    def test_lqtm_checkpoint_skips_same_head_and_is_incremental(self):
        self.assertEqual(
            decision([comment("head", "base", lqtm=True)], "head", "base", {}),
            ("skip", "head"),
        )
        self.assertEqual(
            decision([comment("old", "base", lqtm=True)], "new", "base", {"status": "ahead"}),
            ("incremental", "old"),
        )

    def test_force_push_and_changed_base_are_full(self):
        self.assertEqual(
            decision([comment("old", "base")], "new", "base", {"status": "diverged"}),
            ("full", ""),
        )
        self.assertEqual(
            decision([comment("old", "base")], "new", "other", {"status": "ahead"}),
            ("full", ""),
        )

    def test_spoof_and_non_checkpoint_comments_do_not_skip(self):
        comments = [
            comment("head", "base", login="octocat"),
            {"body": "opencode review completed", "user": {"login": "github-actions[bot]", "type": "Bot"}},
        ]
        self.assertEqual(decision(comments, "head", "base", {}), ("full", ""))

    def test_only_trusted_checkpoint_is_candidate(self):
        self.assertEqual(
            latest_for_base([comment("old", "base"), comment("fake", "base", login="octocat")], "base"),
            "old",
        )

    def test_lqtm_checkpoint_is_candidate(self):
        self.assertEqual(latest_for_base([comment("old", "base", lqtm=True)], "base"), "old")

    def test_quoted_or_embedded_marker_is_not_a_checkpoint(self):
        marker = comment("head", "base")["body"]
        comments = [
            comment("head", "base", body=f"> {marker}"),
            comment("head", "base", body=f"```\n{marker}\n```"),
        ]
        self.assertEqual(decision(comments, "head", "base", {}), ("full", ""))
        self.assertEqual(latest_for_base(comments, "base"), "")


if __name__ == "__main__":
    unittest.main()
