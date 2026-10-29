# Audit search

## Company history

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


## Platform history

`GET /api/v1/administration/audit-events` exposes only global events whose journal
company is null. It requires the original and current **platform** `audit.read`
grant, an active account, current credentials and applicable MFA before and after
acquisition. A company audit grant or `identity.manage` alone does not grant this
read. The transaction has no company scope and holds a shared account guard while
checking current authority and acquiring the page. Permission changes revoke the
account's sessions through the existing identity command.

The time/filter/limit/cursor contract is the same as company history. Each item has
`id`, `actorId`, `resourceType`, `resourceId`, `action`, `correlationId` and
`recordedAt`; the page has `from`, `until`, `evaluatedAt`, `items` and `nextCursor`.
Neither includes a company field. A company event cannot resolve as a global
cursor. This is global account/integration history, not a combined search of all
companies. Reasons and change payloads require a separate source-aware disclosure
contract and are not acquired by this endpoint. The first supported detail is
[saved account access](account-access-audit.md), requiring both global `audit.read`
and `identity.manage`; other source families remain metadata-only.

V99 captures a seven-column append-only metadata projection in
`platform_audit_metadata`. The migration locks journal writers across backfill
and trigger installation, so existing and newly committed global events are
retained. Capture shares the original journal transaction and rolls back with it.
Both functions run with invoker privileges; a source validation trigger requires
an exact existing global journal row. Foreign/company sources or fabricated
metadata cannot be inserted. Existing global journal writes while a company is
selected remain valid, without granting that transaction projection reads.

Forced RLS allows projection reads only in global scope for a currently active
account with the platform grant. The original `audit_entries` RLS is unchanged:
reading another actor's global metadata grants no access to their private journal
payload. Update/delete remain unavailable to runtime readers and the immutable
trigger also rejects privileged rewrites. The descending timestamp/UUID index and
finite `limit + 1` query support the same live-history pagination semantics.

Existing accounts receive no implicit grant during migration or restart. A global
identity administrator can assign `audit.read` through account access review; new
first-run bootstrap administrators include it. The account editor labels it
“Read platform audit” / “Baca audit platform”.

## Dashboard ownership

`/platform/audit` and its navigation item use platform permissions independently
of company membership, including auditors with no companies. The account and
permission snapshot own one page/request. Navigation, account replacement and
hidden workspace disposal abort outstanding reads and clear rows/details; late
success or failure cannot repopulate them. Search and refresh are explicit, with
no background polling or persistent cache. Revoked-session responses revalidate
the workspace and remove the old view.

Global URLs carry `scope=platform` when navigating filters/pages. A company marker
or missing platform marker discards the incoming cursor. Continuations retain the
server-resolved window. The strict global response parser rejects extra scope or
private payload fields, duplicates, invalid order, incompatible filters and
non-progressing page shapes. English/Indonesian table, filters and metadata panels
reuse the company presentation, omitting the company property in global details.


## Global audit validation — 29 October 2026

Across focused runs, 250 distinct backend cases passed: one V98→V99 migration,
35 identity domain, eight administration domain, eleven administration data,
101 audit/identity/API-contract and all 94 worker cases. Both application JARs,
architecture, formatting and twenty recovery-tool tests passed. The final sources
add no dependency on Mobile or Starter.

The migration test backfills only global records, exercises actual non-bypass-RLS
runtime reads/writes, preserves private journal isolation, rejects fabricated or
company-source metadata, and proves rollback, immutability and current account/
grant enforcement. API tests cover tied pagination, exact filters, invalid cursors,
original authority, revocation during an account wait, credential changes, MFA
expiry during acquisition, failure/cancellation and the separate OpenAPI projection.

Dashboard `npm run check` passed all 3,949 tests in 345 files (73.66 seconds), with
architecture, Biome and strict TypeScript. Nine focused Chromium scenarios passed
(22.3 seconds including build/setup), covering both company and platform explorers.
The platform lifecycle binding separately verifies hidden workspace and account
replacement; presentation rendering/focus is tested in Chromium. English desktop
and Indonesian 390-pixel screenshots were inspected.

The real API/PostgreSQL/worker/Chromium fixture passed in 8.5 seconds (53.9 with
setup). An independent MFA operator granted only platform `audit.read` to a reader
without companies. The reader acquired the operator's actual account-access audit,
with no private reason/payload, then paged 52 fictional global journal records
through normal capture triggers (50 + 2). A company event was excluded. A third
company-only auditor could read company audit but was denied global audit. The
operator revoked the global grant; the reader's request returned 401, Refresh
removed the old table and the shell returned to sign-in. Owned services and
private connection metadata were removed.

The full database/content/key recovery exercise passed at schema 99: two encrypted
identity records, 104 objects/2,098,795 bytes, old/current keys, independence from
later source changes and rejection of a wrong retired key. Cleanup was verified.
This does not claim a live deployment, external provider or device acceptance.

Initial test setup needed three corrections: Jackson array iteration must use its
iterator, the account catalogue now intentionally contains three permissions, and
OpenAPI assertions belong in the existing enabled-document fixture. A jsdom Fluent
module import was isolated to the presentation boundary for the lifecycle unit
test; real Fluent rendering passed in Chromium. The first real fixture invocation
lacked the Docker group and was rerun with the documented environment. Final
results above supersede these unsuccessful attempts.

Local logs: `.work/platform-audit-backend-checks.log`,
`.work/platform-audit-identity-final.log` (passing identity cases; obsolete OpenAPI
fixture failure), `.work/platform-audit-contract-final.log`,
`.work/platform-audit-dashboard-final.log`, `.work/platform-audit-browser-checks.log`,
`.work/platform-audit-real-browser-2.log` and `.work/platform-audit-recovery-set.log`.
Distinct backend counts are retained locally in
`.work/platform-audit-validated-counts.json`; tracked evidence and commands here
remain sufficient for continuation from a fresh clone.

```sh
./gradlew --no-daemon :core:database:test --tests '*PlatformAuditMigrationTest' \
  :modules:identity:domain:test :modules:administration:domain:test \
  :modules:administration:data:test :apps:server:test --tests '*Audit*Test' \
  --tests '*AccountAdministration*Test' --tests '*IdentityAdministration*Test' \
  --tests '*ApiContractHttpTest' :apps:worker:test \
  :apps:server:bootJar :apps:worker:bootJar architectureCheck formatCheck recoverySetCheck
python3 tool/check_recovery_set.py
cd apps/dashboard
npm run check
npx playwright test tests/platform-audit.spec.ts tests/audit.spec.ts
npx playwright test --config playwright.worker-integration.config.ts integration-worker/platform-audit.spec.ts
```

Use the JDK 21/Docker-group environment in [agent handoff](agent-handoff.md).
The real workflow is part of the existing worker browser suite in CI; hosted CI
success is not asserted from local results.
