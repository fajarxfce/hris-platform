# Company audit search

`GET /api/v1/companies/{companyId}/audit-events`

Requires current company-scoped `audit.read`, an active account/membership/company, the current credential version, and applicable MFA assurance. The use case checks original and live authority after shared company, membership, and account guards. Audit search remains available for authorized operational recovery during module maintenance and client-version gates.

## Query and response

| Parameter | Contract |
| --- | --- |
| `from` | Inclusive UTC instant. Defaults to 30 days before `until`. |
| `until` | Exclusive UTC instant. Defaults to request evaluation time and cannot be in the future. |
| `limit` | 1–200; default 50. |
| `cursor` | Last returned event ID from `nextCursor`. Keep the original window and filters on continuation. |
| `actorId` | Optional exact actor UUID. |
| `resourceType`, `resourceId` | Optional exact resource code/UUID. |
| `action` | Optional exact action code. |

Time windows span at most 90 days, begin on or after 1900-01-01, and use database microsecond precision. Code filters accept bounded lower-case letters, digits, underscores, dots, colons, and hyphens. Values are bound as SQL parameters.

The response contains `companyId`, the resolved `from`/`until`, `evaluatedAt`, `items`, and `nextCursor`. Each item contains only `id`, `companyId`, `actorId`, `resourceType`, `resourceId`, `action`, `correlationId`, and `recordedAt`. Names, reasons, change payloads, account credentials, and employee profiles are outside this projection. The datasource selects only these metadata columns. Disclosure of detailed changes requires a separate source-aware access contract.

Events are ordered by `recordedAt DESC, id DESC`. The cursor identifies an immutable record, avoiding timestamp ties and offset shifts. Missing, foreign-company, out-of-window, or mismatched-filter cursors return `invalid_audit_cursor` with no resource detail. Global account events are excluded even when the database permits the current actor to read their own global journal. Every continuation repeats current authorization; knowing a cursor never grants access.

## Consistency and mobile clients

Each page reads one bounded SQL snapshot. The immutable cursor record can be resolved before that query without locking audit writers. New commits can appear in a fresh search, including records whose transaction began before a previously read page. Pagination is a live history view, not an immutable multi-page export or a commit-ordered replication feed. Use the [sync API](synchronization.md) for local-first replication, and restart audit search to include newly committed events.

Persist the returned time window together with all filters and cursor when navigating pages. Scope client state by account/company, discard it after access failure, and cancel obsolete requests when the scope changes. Responses use `no-store`. Safe error codes include `invalid_pagination`, `invalid_audit_range` (with `maximumDays`), `invalid_audit_filter`, `invalid_audit_cursor`, and the shared authentication/database codes. Clients translate these codes; raw diagnostics never enter the response.

Queries fetch at most `limit + 1` metadata rows and retain standard transaction, lock, and query deadlines. There is no polling loop, subscription, export buffer, or new audit mutation endpoint. The descending company/time/ID index matches the continuation order. Cursor records and pages remain subject to existing append-only database protections.
