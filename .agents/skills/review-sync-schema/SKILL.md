---
name: review-sync-schema
description: Review synchronization contracts and database migrations across this project's Android Room entities, Gson request/response DTOs, Go API models and validation, repository SQL, and PostgreSQL schema. Use before releasing changes that add, remove, rename, default, validate, or persist synchronized fields; when modifying Room or SQL migrations; when diagnosing sync HTTP 400/500 errors, data type resets, missing fields, or Android/backend database inconsistencies; and when reviewing PRs that touch sync, categories, expenses, repositories, models, or migrations.
---

# Review Sync Schema

Prevent schema drift between Android, the sync API, and PostgreSQL. Treat review as read-only unless the user also asks for fixes.

## Start

1. Read [references/kakeibo-contract.md](references/kakeibo-contract.md).
2. Run:

   ```bash
   python3 .agents/skills/review-sync-schema/scripts/collect_contract.py --root .
   ```

3. Inspect the complete contract even when only one layer changed. A serialized Android field can break an unchanged strict Go decoder.
4. Inspect `git diff` separately to identify the proposed delta; do not limit the compatibility review to changed files.

## Build the contract matrix

Create one row per synchronized field and record:

| Field | Android Room | Android JSON | Go JSON/model | Validation/default | Repository SQL | PostgreSQL | Migration/backfill |
|---|---|---|---|---|---|---|---|

For every row, verify all of these gates:

1. **Android → API encoding**
   - Confirm `@Expose`, `@SerializedName`, nullability, enum values, and local-only exclusions.
   - Treat a new exposed field as breaking when the server calls `DisallowUnknownFields`.
2. **API decoding and validation**
   - Confirm the Go JSON tag and type match the Android wire name and representation.
   - Confirm validators accept every client value and intentionally handle legacy missing values.
3. **API → database persistence**
   - Confirm every insert, update, conflict clause, select, scan, changes query, trash query, and restore path handles the field.
4. **API → Android decoding**
   - Confirm server responses return the field and Android merge logic cannot replace a valid local value with an absent/default server value.
5. **Database constraints**
   - Align types, defaults, nullability, checks, foreign keys, and timestamp semantics.
6. **Conflict resolution**
   - Confirm `updated_at`, deletion, and equal-timestamp behavior remain consistent in both directions.

Do not accept matching names alone as proof. Verify reads, writes, conflicts, and response serialization.

## Review migration paths

Evaluate all four paths:

1. Fresh Android install.
2. Android upgrade from every supported Room version.
3. Fresh PostgreSQL volume.
4. Existing PostgreSQL volume with user data.

Require complete Room migration chains and an executable server migration path. Files mounted in `docker-entrypoint-initdb.d` do not migrate an existing PostgreSQL volume. Require an idempotent startup migration, migration runner, or explicit deployment step.

For a new non-null field, verify:

- a safe schema default;
- a semantic backfill for existing rows;
- preservation of local-only state;
- compatibility with old clients during rollout;
- downgrade or rollback implications.

Flag a generic default when it silently changes meaning, such as converting an income category to expense.

## Verify

Prefer deterministic tests over manual inspection:

- Decode a realistic Android sync JSON payload in Go.
- Test accepted enum values, rejected values, and legacy omitted fields.
- Test repository round trips for insert, update, older-conflict, list, changes, trash, and restore.
- Test Room migrations from prior versions.
- Run Backend tests and Android unit tests.
- When Docker is available, migrate a copy or disposable database and send a realistic sync request.

Do not modify production or user records for a review. Use a disposable database, a rolled-back transaction, or a request guaranteed to fail before persistence. If the user explicitly asks to deploy a fix, back up or preserve the existing volume and verify row counts and representative records before and after.

## Report

Lead with findings ordered by severity:

- **P0/P1**: sync blocked, data loss, semantic corruption, or migration failure;
- **P2**: partial-path omission, legacy incompatibility, or weak validation;
- **P3**: missing test or documentation that does not currently break behavior.

For each finding, cite exact files and lines, identify the failing direction and migration path, and describe the smallest safe remediation. If no issue is found, state which contract gates and migration paths were verified and list any untested runtime assumptions.

End with a release verdict:

- `BLOCK`: any P0/P1 or unverified destructive migration;
- `CONDITIONAL`: P2 or an unavailable required runtime test;
- `PASS`: all contract gates, migration paths, and relevant tests succeed.
