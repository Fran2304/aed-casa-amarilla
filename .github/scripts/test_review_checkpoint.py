import json
import unittest

from review_checkpoint import decision, latest_for_base


def comment(head, base, login="github-actions[bot]", author_type="Bot"):
    return {
        "body": f"<!-- opencode-review-checkpoint:v1 workflow=opencode-review head_sha={head} base_sha={base} -->",
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


if __name__ == "__main__":
    unittest.main()
