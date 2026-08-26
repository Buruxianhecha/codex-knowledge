#!/usr/bin/env python3
"""Validate codex-knowledge structure, metadata, links, lifecycle and privacy.

Stdlib-only so the same audit runs locally and in GitHub Actions.
Errors are contradictions/broken invariants. Warnings are unresolved debt.
Legacy overrides are explicit compatibility metadata, not hidden debt.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROOT / "metadata/legacy-overrides.json"

CORE_DIRS = {
    "projects": "项目",
    "lessons": "经验",
    "patterns": "模式",
    "mistakes": "错误",
    "decisions": "决策",
    "anti-patterns": "反模式",
    "templates": "模板",
    "bad-cases": "失败案例",
}
META_DIRS = {"projects", "lessons", "patterns", "mistakes", "decisions", "anti-patterns"}
ALLOWED_STATUS = {"active", "verified", "best_practice", "deprecated", "superseded", "archived"}
COMMON_META = {"status", "confidence", "reuse_count", "last_used", "verified_in", "expires_after"}
ALLOWED_OVERRIDE_FIELDS = COMMON_META | {"cross_refs", "replaced_by"}

errors: list[str] = []
warnings: list[str] = []
legacy_files: dict[str, dict[str, object]] = {}
decision_costs: dict[str, dict[str, str]] = {}


def err(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def all_files(path: Path) -> list[Path]:
    return sorted(p for p in path.rglob("*") if p.is_file()) if path.exists() else []


def parse_inline_list(value: str) -> list[str]:
    value = value.strip()
    if not (value.startswith("[") and value.endswith("]")):
        return []
    body = value[1:-1].strip()
    if not body:
        return []
    return [x.strip().strip("'\"") for x in body.split(",") if x.strip()]


def frontmatter(text: str) -> tuple[dict[str, object], int]:
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return {}, 0
    end = 0
    for i in range(1, len(lines)):
        if lines[i].strip() == "---":
            end = i
            break
    if not end:
        return {}, 0

    meta: dict[str, object] = {}
    current_list: str | None = None
    for raw in lines[1:end]:
        if raw.startswith((" ", "\t")):
            stripped = raw.strip()
            if current_list and stripped.startswith("- "):
                assert isinstance(meta[current_list], list)
                meta[current_list].append(stripped[2:].strip().strip("'\""))
            continue
        current_list = None
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_-]*):\s*(.*)$", raw)
        if not m:
            continue
        key, value = m.group(1), m.group(2).strip()
        if not value:
            meta[key] = []
            current_list = key
        elif value.startswith("[") and value.endswith("]"):
            meta[key] = parse_inline_list(value)
        else:
            meta[key] = value.strip("'\"")
    return meta, end + 1


def int_value(v: object, default: int = -1) -> int:
    try:
        return int(str(v))
    except Exception:
        return default


def float_value(v: object, default: float = -1.0) -> float:
    try:
        return float(str(v))
    except Exception:
        return default


def ref_path(raw: str) -> str:
    raw = raw.strip().strip("'\"")
    raw = re.sub(r"\s+\([^)]*\)\s*$", "", raw)
    return raw.split("#", 1)[0].strip()


def load_legacy_registry() -> None:
    if not REGISTRY_PATH.exists():
        warn("legacy metadata registry missing")
        return
    try:
        data = json.loads(read(REGISTRY_PATH))
    except Exception as exc:
        err(f"legacy metadata registry invalid JSON: {exc}")
        return

    if data.get("schema_version") != 1:
        err(f"unsupported legacy registry schema: {data.get('schema_version')!r}")

    raw_files = data.get("files", {})
    raw_costs = data.get("decision_costs", {})
    if not isinstance(raw_files, dict) or not isinstance(raw_costs, dict):
        err("legacy registry files/decision_costs must be objects")
        return

    for rel, item in raw_files.items():
        path = ROOT / rel
        if not path.is_file():
            err(f"legacy override points to missing file: {rel}")
            continue
        if not isinstance(item, dict) or not str(item.get("reason", "")).strip():
            err(f"legacy override missing reason: {rel}")
            continue
        fields = item.get("fields", {})
        if not isinstance(fields, dict):
            err(f"legacy override fields must be object: {rel}")
            continue
        unknown = sorted(set(fields) - ALLOWED_OVERRIDE_FIELDS)
        if unknown:
            err(f"legacy override has unsupported fields: {rel}: {unknown}")
            continue

        inline, _ = frontmatter(read(path))
        duplicates = sorted(set(fields) & set(inline))
        if duplicates:
            err(f"legacy override duplicates inline metadata; migrate/remove override: {rel}: {duplicates}")
            continue
        legacy_files[rel] = fields

    for rel, item in raw_costs.items():
        path = ROOT / rel
        if not path.is_file() or not rel.startswith("decisions/"):
            err(f"decision cost override points to invalid file: {rel}")
            continue
        if not isinstance(item, dict):
            err(f"decision cost override must be object: {rel}")
            continue
        level = str(item.get("level", ""))
        reason = str(item.get("reason", "")).strip()
        if level not in {"low", "medium", "high"} or not reason:
            err(f"decision cost override invalid: {rel}")
            continue
        inline, _ = frontmatter(read(path))
        if "cost" in inline:
            err(f"decision already has inline cost; remove registry override: {rel}")
            continue
        decision_costs[rel] = {"level": level, "reason": reason}


def effective_meta(rel: str, inline: dict[str, object]) -> dict[str, object]:
    merged = dict(inline)
    for key, value in legacy_files.get(rel, {}).items():
        if key not in merged:
            merged[key] = value
    return merged


def validate_counts() -> None:
    actual = {name: len(all_files(ROOT / name)) for name in CORE_DIRS}
    total = sum(actual.values())
    readme = read(ROOT / "README.md")
    index = read(ROOT / "KNOWLEDGE_INDEX.md")
    bundle = read(ROOT / "KNOWLEDGE_BUNDLE.md")

    for folder, label in CORE_DIRS.items():
        m = re.search(rf"\|\s*{re.escape(label)}\s*`{re.escape(folder)}/`\s*\|\s*(\d+)\s*\|", readme)
        if not m:
            err(f"README missing count row for {folder}")
        elif int(m.group(1)) != actual[folder]:
            err(f"README count mismatch for {folder}: declared {m.group(1)}, actual {actual[folder]}")

        m2 = re.search(rf"^##\s+{re.escape(label)}\s*\((\d+)\)", index, re.M)
        if not m2:
            err(f"INDEX missing section count for {label}")
        elif int(m2.group(1)) != actual[folder]:
            err(f"INDEX count mismatch for {folder}: declared {m2.group(1)}, actual {actual[folder]}")

    canonical_totals = {
        "README.md": (readme, r"\|\s*合计\s*\|\s*(\d+)\s*\|"),
        "KNOWLEDGE_INDEX.md": (index, r"核心条目[:：]\s*(\d+)"),
        "KNOWLEDGE_BUNDLE.md": (bundle, r"当前有\s*(\d+)\s*个核心条目"),
    }
    for filename, (text, pattern) in canonical_totals.items():
        m = re.search(pattern, text)
        if not m:
            err(f"{filename} missing canonical total count")
        elif int(m.group(1)) != total:
            err(f"{filename} total count mismatch: declared {m.group(1)}, actual {total}")


def validate_metadata() -> None:
    for folder in META_DIRS:
        for path in all_files(ROOT / folder):
            if path.suffix.lower() != ".md":
                continue
            rel = path.relative_to(ROOT).as_posix()
            inline, _ = frontmatter(read(path))
            meta = effective_meta(rel, inline)
            if not meta:
                warn(f"metadata missing: {rel}")
                continue
            missing = sorted(k for k in COMMON_META if k not in meta)
            if missing:
                warn(f"metadata incomplete: {rel}: missing {', '.join(missing)}")

            status = str(meta.get("status", ""))
            if status and status not in ALLOWED_STATUS:
                err(f"invalid status {status!r}: {rel}")
            if "confidence" in meta:
                conf = float_value(meta["confidence"])
                if not 0.0 <= conf <= 1.0:
                    err(f"invalid confidence {meta['confidence']!r}: {rel}")
            if "reuse_count" in meta and int_value(meta["reuse_count"]) < 0:
                err(f"invalid reuse_count {meta['reuse_count']!r}: {rel}")

            verified_in = meta.get("verified_in", [])
            if not isinstance(verified_in, list):
                verified_in = parse_inline_list(str(verified_in))
            reuse = int_value(meta.get("reuse_count", -1))
            if folder == "patterns" and status == "verified" and len(set(verified_in)) < 2:
                err(f"pattern promoted too early: {rel}: verified_in={verified_in}")
            if status == "best_practice" and (len(set(verified_in)) < 3 or reuse < 3):
                err(f"best_practice lacks reuse evidence: {rel}: verified_in={verified_in}, reuse_count={reuse}")

            refs = meta.get("cross_refs", [])
            if isinstance(refs, list):
                for raw in refs:
                    target = ref_path(str(raw))
                    if target and not (ROOT / target).exists():
                        err(f"broken cross_ref: {rel} -> {target}")

    for path in all_files(ROOT / "decisions"):
        if path.suffix.lower() != ".md":
            continue
        rel = path.relative_to(ROOT).as_posix()
        inline, _ = frontmatter(read(path))
        meta = effective_meta(rel, inline)
        if meta and "cost" not in meta and rel not in decision_costs:
            warn(f"decision missing cost block: {rel}")


def validate_bad_cases() -> None:
    required = {"source", "date", "category", "severity"}
    for path in all_files(ROOT / "bad-cases"):
        if path.suffix.lower() != ".md":
            continue
        rel = path.relative_to(ROOT).as_posix()
        meta, _ = frontmatter(read(path))
        missing = sorted(required - set(meta))
        if missing:
            warn(f"bad-case provenance incomplete: {rel}: missing {', '.join(missing)}")


def validate_markdown_links() -> None:
    md_files = [p for p in ROOT.rglob("*.md") if ".git" not in p.parts]
    link_re = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
    for path in md_files:
        rel = path.relative_to(ROOT).as_posix()
        text = read(path)
        for target in link_re.findall(text):
            target = target.strip().split()[0].strip("<>\"")
            if not target or target.startswith(("http://", "https://", "mailto:", "#")):
                continue
            target = unquote(target.split("#", 1)[0])
            if not target:
                continue
            candidate = (ROOT / target.lstrip("/")) if target.startswith("/") else (path.parent / target)
            if not candidate.exists():
                err(f"broken markdown link: {rel} -> {target}")


def validate_versions_and_compact() -> None:
    def version(path: str, token: str) -> int | None:
        m = re.search(rf"{re.escape(token)}[^\n]*?v(\d+)", read(ROOT / path), re.I)
        return int(m.group(1)) if m else None

    index_v = version("KNOWLEDGE_INDEX.md", "Knowledge Index")
    bundle_v = version("KNOWLEDGE_BUNDLE.md", "完整知识包")
    memory_v = version("memory/distilled-memory.md", "Distilled Memory")

    for starter in (ROOT / "compact/L1-3files/01-START-HERE.md", ROOT / "compact/L2-7files/01-START-HERE.md"):
        text = read(starter)
        m = re.search(r"源版本[:：]\s*index v(\d+)\s*/\s*memory v(\d+)\s*/\s*bundle v(\d+)", text, re.I)
        rel = starter.relative_to(ROOT).as_posix()
        if not m:
            warn(f"compact source versions missing: {rel}")
            continue
        declared = tuple(map(int, m.groups()))
        current = (index_v, memory_v, bundle_v)
        if declared != current:
            err(f"compact stale: {rel}: source={declared}, current={current}")


def validate_privacy() -> None:
    secret_patterns = {
        "GitHub token": re.compile(r"\bghp_[A-Za-z0-9]{20,}\b|\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
        "OpenAI-like secret": re.compile(r"\bsk-[A-Za-z0-9_-]{24,}\b"),
    }
    win_user = re.compile(r"\b[A-Za-z]:\\Users\\([^\\\s/`]+)", re.I)
    unix_user = re.compile(r"/(?:Users|home)/([^/\s]+)/")
    placeholders = {"user", "username", "name", "example", "yourname", "<user>", "<username>"}

    for path in (p for p in ROOT.rglob("*") if p.is_file() and p.suffix.lower() in {".md", ".py", ".ps1", ".env", ".json", ".yml", ".yaml", ""}):
        try:
            text = read(path)
        except UnicodeDecodeError:
            continue
        rel = path.relative_to(ROOT).as_posix()
        for label, pattern in secret_patterns.items():
            if pattern.search(text):
                err(f"possible {label} committed: {rel}")
        for pattern in (win_user, unix_user):
            for m in pattern.finditer(text):
                name = m.group(1).strip("<>%{}").lower()
                if name not in placeholders and not name.startswith("$env"):
                    err(f"user-specific home path exposed in {rel}: {m.group(0)}")


def main() -> int:
    load_legacy_registry()
    validate_counts()
    validate_metadata()
    validate_bad_cases()
    validate_markdown_links()
    validate_versions_and_compact()
    validate_privacy()

    print("Knowledge audit")
    print(f"errors={len(errors)} warnings={len(warnings)}")
    print(f"legacy_metadata_overrides={len(legacy_files)} decision_cost_overrides={len(decision_costs)}")
    if errors:
        print("\nERRORS")
        for item in errors:
            print(f"- {item}")
    if warnings:
        print("\nWARNINGS")
        for item in warnings:
            print(f"- {item}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
