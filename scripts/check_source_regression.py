#!/usr/bin/env python3
"""Fail CI when a protected knowledge source is unexpectedly over-compressed.

This guard complements schema/link/privacy validation. It detects a class of
regression that can still produce perfectly valid Markdown and green metadata:
large deletion of reusable source knowledge.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ALLOW_MARKER = "[allow-source-compaction]"
MIN_OLD_LINES = 20
MIN_RETAIN_RATIO = 0.60

PROTECTED_PREFIXES = (
    "projects/",
    "lessons/",
    "patterns/",
    "mistakes/",
    "decisions/",
    "anti-patterns/",
    "bad-cases/",
    "templates/",
    "preferences/",
)
PROTECTED_ROOT_FILES = {
    "SYSTEM.md",
    "FRESHNESS.md",
    "QUALITY.md",
    ".value-rules.md",
    ".codex-instructions.md",
}


def git(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=check,
    )


def protected(path: str) -> bool:
    return path in PROTECTED_ROOT_FILES or path.startswith(PROTECTED_PREFIXES)


def line_count(text: str) -> int:
    return len(text.splitlines())


def main() -> int:
    parent = git("rev-parse", "HEAD^", check=False)
    if parent.returncode != 0:
        print("Source regression guard: no parent commit available; skipped")
        return 0
    parent_sha = parent.stdout.strip()

    message = git("log", "-1", "--pretty=%B").stdout
    if ALLOW_MARKER.lower() in message.lower():
        print(f"Source regression guard bypassed by explicit marker {ALLOW_MARKER}")
        return 0

    changed = git("diff", "--name-status", "--find-renames", parent_sha, "HEAD").stdout.splitlines()
    errors: list[str] = []
    checked = 0

    for raw in changed:
        if not raw.strip():
            continue
        parts = raw.split("\t")
        status = parts[0]
        path = parts[-1]
        if not protected(path):
            continue
        if not path.lower().endswith((".md", ".py", ".ps1", ".json", ".yml", ".yaml", ".env")):
            continue

        if status.startswith("A"):
            continue
        if status.startswith("D"):
            errors.append(f"protected source deleted: {path}")
            continue
        if status.startswith("R"):
            old_path = parts[1]
        else:
            old_path = path

        old = git("show", f"{parent_sha}:{old_path}", check=False)
        current_path = ROOT / path
        if old.returncode != 0 or not current_path.is_file():
            continue
        try:
            new_text = current_path.read_text(encoding="utf-8-sig")
        except UnicodeDecodeError:
            continue

        old_lines = line_count(old.stdout)
        new_lines = line_count(new_text)
        if old_lines < MIN_OLD_LINES:
            continue
        checked += 1
        ratio = new_lines / old_lines if old_lines else 1.0
        if ratio < MIN_RETAIN_RATIO:
            errors.append(
                f"protected source shrank too much: {path}: "
                f"{old_lines} -> {new_lines} lines ({ratio:.0%} retained); "
                f"review diff or use {ALLOW_MARKER} after explicit approval"
            )

    print(f"Source regression guard: checked={checked} errors={len(errors)}")
    if errors:
        for item in errors:
            print(f"- {item}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
