import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
WORKFLOWS = ROOT / ".github/workflows"
SCRIPTS = ROOT / ".github/scripts"

# Marcas de la maquinaria de review removida: ningún workflow restante debe referenciarlas.
REMOVED_MARKERS = ("opencode-review", "review_checkpoint", "anomalyco/opencode")


class ReviewRemovalTest(unittest.TestCase):
    def test_review_artifacts_are_removed(self):
        self.assertFalse((WORKFLOWS / "opencode-review.yml").exists())
        self.assertFalse((SCRIPTS / "review_checkpoint.py").exists())
        self.assertFalse((SCRIPTS / "test_review_checkpoint.py").exists())

    def test_compiler_workflow_keeps_required_intent(self):
        path = WORKFLOWS / "compilar.yml"
        self.assertTrue(path.exists())
        text = path.read_text(encoding="utf-8")
        self.assertIn("push", text)
        self.assertIn("pull_request", text)
        self.assertIn('java-version: "21"', text)
        self.assertIn("find src -name \"*.java\" > sources.txt", text)
        self.assertIn("javac -encoding UTF-8 -Xlint:none -d out @sources.txt", text)

    def test_no_remaining_workflow_references_to_removed_review(self):
        for path in WORKFLOWS.iterdir():
            if not path.is_file() or path.suffix not in (".yml", ".yaml"):
                continue
            text = path.read_text(encoding="utf-8")
            for marker in REMOVED_MARKERS:
                self.assertNotIn(marker, text, f"{path.name} references removed {marker!r}")

    def test_git_ignores_pycache_probe(self):
        result = subprocess.run(
            ["git", "check-ignore", ".github/scripts/__pycache__/probe.pyc"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("__pycache__", result.stdout)


if __name__ == "__main__":
    unittest.main()
