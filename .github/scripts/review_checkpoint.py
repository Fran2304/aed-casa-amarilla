#!/usr/bin/env python3
"""Read and validate the bot comments used as successful review checkpoints."""
from __future__ import annotations

import json
import re
import sys


MARKER = "opencode-review-checkpoint:v1"
MARKER_RE = re.compile(
    rf"<!--\s*{re.escape(MARKER)}\s+workflow=opencode-review\s+head_sha=(\S+)\s+base_sha=(\S+)\s*-->"
)


def comments_from_payload(payload: object) -> list[dict[str, object]]:
    if isinstance(payload, list):
        result: list[dict[str, object]] = []
        for item in payload:
            result.extend(comments_from_payload(item))
        return result
    return [payload] if isinstance(payload, dict) else []


def checkpoints(payload: object) -> list[tuple[str, str]]:
    found = []
    for comment in comments_from_payload(payload):
        author = comment.get("user")
        if not isinstance(author, dict) or author.get("login") != "github-actions[bot]":
            continue
        if author.get("type") not in (None, "Bot"):
            continue
        match = MARKER_RE.fullmatch(str(comment.get("body", "")).strip())
        if match:
            found.append((match.group(1), match.group(2)))
    return found


def decision(payload: object, head: str, base: str, compare: object) -> tuple[str, str]:
    """Return (mode, checkpoint): skip, incremental, or full."""
    valid = [(sha, marker_base) for sha, marker_base in checkpoints(payload) if marker_base == base]
    if any(sha == head for sha, _ in valid):
        return "skip", head
    if not valid:
        return "full", ""
    last_sha = valid[-1][0]
    if isinstance(compare, dict) and compare.get("status") in ("ahead", "identical"):
        return "incremental", last_sha
    return "full", ""


def latest_for_base(payload: object, base: str) -> str:
    valid = [sha for sha, marker_base in checkpoints(payload) if marker_base == base]
    return valid[-1] if valid else ""


def main() -> None:
    if len(sys.argv) == 3 and sys.argv[1] == "candidate":
        print(latest_for_base(json.load(sys.stdin), sys.argv[2]))
        return
    if len(sys.argv) != 4:
        raise SystemExit("usage: review_checkpoint.py HEAD BASE COMPARE_JSON")
    payload = json.load(sys.stdin)
    compare = json.loads(sys.argv[3])
    mode, checkpoint = decision(payload, sys.argv[1], sys.argv[2], compare)
    print(json.dumps({"mode": mode, "last_successful_sha": checkpoint}))


if __name__ == "__main__":
    main()
