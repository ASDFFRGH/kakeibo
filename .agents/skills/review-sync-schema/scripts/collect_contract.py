#!/usr/bin/env python3
"""Collect line-numbered synchronization contract evidence without changing files."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


FILE_PATTERNS: tuple[tuple[str, tuple[str, ...]], ...] = (
    (
        "android/app/src/main/java/jp/local/kakeibo/data/Models.kt",
        (
            r"@Entity|@Database|@ColumnInfo|@SerializedName|@Expose",
            r"data class (Category|Expense|Sync)|MIGRATION_|version\s*=",
            r"CREATE TABLE|ALTER TABLE|INSERT OR IGNORE",
        ),
    ),
    (
        "android/app/src/main/java/jp/local/kakeibo/data/KakeiboRepository.kt",
        (
            r"SyncRequest|SyncResponse|unsynced|lastSynced|serverTime",
            r"isSynced|withTransaction|copy\(",
        ),
    ),
    (
        "backend/internal/model/model.go",
        (r"type (Category|Expense|SyncRequest|SyncData)", r'json:"'),
    ),
    (
        "backend/internal/api/api.go",
        (
            r"DisallowUnknownFields|validCategory|validExpense|func \(a \*API\) sync",
            r"invalid sync|TransactionType",
        ),
    ),
    (
        "backend/internal/repository/repository.go",
        (
            r"ListCategories|SaveCategory|GetCategory|changedCategories",
            r"ListExpenses|SaveExpense|GetExpense|changedExpenses",
            r"SELECT |INSERT INTO|UPDATE |ON CONFLICT|RETURNING ",
            r"transaction_type|updated_at|deleted_at",
        ),
    ),
    (
        "backend/internal/database/database.go",
        (r"migrat|ALTER TABLE|constraint|CREATE INDEX",),
    ),
    (
        "android/app/build.gradle.kts",
        (r"versionCode|versionName|API_BASE_URL",),
    ),
    (
        "docker-compose.yml",
        (r"migrations|postgres_data|depends_on|DATABASE_URL",),
    ),
)

CORE_FILES = {
    "android/app/src/main/java/jp/local/kakeibo/data/Models.kt",
    "android/app/src/main/java/jp/local/kakeibo/data/KakeiboRepository.kt",
    "backend/internal/model/model.go",
    "backend/internal/api/api.go",
    "backend/internal/repository/repository.go",
}


def matching_lines(path: Path, patterns: tuple[str, ...]) -> list[tuple[int, str]]:
    compiled = [re.compile(pattern) for pattern in patterns]
    result: list[tuple[int, str]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if any(pattern.search(line) for pattern in compiled):
            result.append((line_number, line.rstrip()))
    return result


def changed_files(root: Path) -> list[str]:
    tracked_process = subprocess.run(
        ["git", "diff", "--name-only", "HEAD"],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    untracked_process = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard"],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    if tracked_process.returncode != 0 or untracked_process.returncode != 0:
        return []
    tracked = {line for line in tracked_process.stdout.splitlines() if line}
    untracked = {line for line in untracked_process.stdout.splitlines() if line}
    return sorted(tracked | untracked)


def is_sync_related(path: str) -> bool:
    markers = (
        "Models.kt",
        "KakeiboRepository.kt",
        "backend/internal/api/",
        "backend/internal/database/",
        "backend/internal/model/",
        "backend/internal/repository/",
        "backend/migrations/",
        "docker-compose",
        "sync",
    )
    return any(marker in path for marker in markers)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="Repository root")
    args = parser.parse_args()
    root = args.root.resolve()

    missing_core: list[str] = []
    print("# Synchronization contract evidence")
    print(f"\nRepository: `{root}`")

    changed = changed_files(root)
    print("\n## Changed files")
    if changed:
        for path in changed:
            marker = " **sync-related**" if is_sync_related(path) else ""
            print(f"- `{path}`{marker}")
    else:
        print("- No tracked or untracked working-tree changes detected.")

    for relative_path, patterns in FILE_PATTERNS:
        path = root / relative_path
        print(f"\n## `{relative_path}`")
        if not path.exists():
            print("- MISSING")
            if relative_path in CORE_FILES:
                missing_core.append(relative_path)
            continue
        lines = matching_lines(path, patterns)
        if not lines:
            print("- No matching contract statements.")
            continue
        for line_number, line in lines:
            print(f"- `{relative_path}:{line_number}` `{line.strip()}`")

    migrations = sorted((root / "backend/migrations").glob("*.sql"))
    print("\n## `backend/migrations/*.sql`")
    if not migrations:
        print("- MISSING")
    for migration in migrations:
        for line_number, line in enumerate(migration.read_text(encoding="utf-8").splitlines(), 1):
            stripped = line.strip()
            if stripped and not stripped.startswith("--"):
                relative = migration.relative_to(root)
                print(f"- `{relative}:{line_number}` `{stripped}`")

    print("\n## Review reminder")
    print("- Build the field-by-field matrix; this output is evidence, not a PASS result.")
    print("- Verify Android→API, API→DB, DB→API, and API→Android directions.")
    print("- Verify fresh and upgrade paths for both Room and PostgreSQL.")

    if missing_core:
        print("\nERROR: missing core contract files:", file=sys.stderr)
        for path in missing_core:
            print(f"- {path}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
