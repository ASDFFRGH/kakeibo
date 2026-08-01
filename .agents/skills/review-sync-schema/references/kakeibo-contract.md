# Kakeibo synchronization contract

Use this file as project-specific review guidance, then derive the current contract from source. Update this reference when the intended domain contract changes.

## Source map

- Android entities, Room schema, and migrations: `android/app/src/main/java/jp/local/kakeibo/data/Models.kt`
- Android sync request, response, and merge: `android/app/src/main/java/jp/local/kakeibo/data/KakeiboRepository.kt`
- Android base URL: `android/app/build.gradle.kts`
- Go wire models: `backend/internal/model/model.go`
- Strict decoding and validation: `backend/internal/api/api.go`
- PostgreSQL reads, writes, conflicts, changes, trash, and restore: `backend/internal/repository/repository.go`
- Runtime database migration: `backend/internal/database/database.go`
- Fresh database and incremental SQL: `backend/migrations/*.sql`
- Container lifecycle: `docker-compose.yml`, `backend/Dockerfile`

## Intended field invariants

### Category

| Meaning | Android/JSON | PostgreSQL | Notes |
|---|---|---|---|
| ID | `uuid` | `uuid` | UUID string on wire |
| Name | `name` | `name` | Non-blank |
| Transaction type | `type` | `transaction_type` | `expense` or `income` |
| Created | `created_at` | `created_at` | RFC 3339 / timestamptz |
| Updated | `updated_at` | `updated_at` | Conflict-resolution clock |
| Deleted | `deleted_at` | `deleted_at` | Nullable logical deletion |
| Pending sync | `is_synced` | none | Android-local only; never expose |

### Expense

| Meaning | Android/JSON | PostgreSQL | Notes |
|---|---|---|---|
| ID | `uuid` | `uuid` | UUID string on wire |
| Date | `date` | `expense_date` | `YYYY-MM-DD` |
| Amount | `amount` | `amount` | Positive integer |
| Transaction type | `type` | `transaction_type` | `expense` or `income` |
| Category | `category_uuid` | `category_uuid` | Valid category UUID |
| Memo | `memo` | `memo` | String |
| Created/updated/deleted | snake-case wire names | matching timestamp columns | Same semantics as category |
| Pending sync | `is_synced` | none | Android-local only; never expose |

### Sync envelope

- Request: `last_synced_at`, `categories`, `expenses`.
- Response: `success`, `server_time`, `synced`, `data.categories`, `data.expenses`.
- Android sends only rows with `is_synced = false`.
- Server returns changes since the last successful server timestamp.
- Newer `updated_at` wins; review the equal-timestamp rule explicitly.

## Known failure pattern

The Android category entity once exposed `type` while the Go `Category` struct omitted it. The API decoder used `DisallowUnknownFields`, so any unsynced category produced HTTP 400 `invalid sync request` before validation or persistence.

This incident demonstrates the required review rule:

> Adding an Android `@Expose` field is an API contract change even when no Backend file changed.

The complete fix required all of:

1. Go model and validation support.
2. Repository insert, conflict update, select, scan, changes, and trash support.
3. A PostgreSQL column, check constraint, and index.
4. Migration of existing Docker volumes, not only fresh initialization.
5. Compatibility for old clients that omit `type`.
6. A realistic JSON decoding test.

## High-risk review signals

- `@Expose`, `@SerializedName`, Go `json:` tags, or `DisallowUnknownFields`.
- Room `version`, `Migration`, `ALTER TABLE`, or new non-null entity properties.
- PostgreSQL `ALTER TABLE`, constraints, defaults, or enum-like checks.
- Repository `SELECT`/`Scan` position changes.
- `INSERT ... ON CONFLICT` that omits a new field.
- Changes queries that return fewer fields than normal list queries.
- Response normalization that converts missing values to a semantic default.
- Docker migrations added only under `docker-entrypoint-initdb.d`.
- A release that updates Android before the accepting Backend is deployed.

## Minimum regression scenarios

1. Fresh vCurrent Android syncs its initial categories and expenses.
2. Upgraded Android syncs rows created under the previous Room schema.
3. Income and expense categories round-trip without changing type.
4. Old clients that omit newly optional fields remain valid during rollout.
5. Unknown or invalid enum values fail with a specific error.
6. Older client data cannot overwrite newer server data.
7. Server changes decode on Android without resetting local semantics.
8. Existing PostgreSQL rows survive migration with correct backfilled meaning.
