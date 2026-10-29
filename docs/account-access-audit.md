# Saved account-access audit

The [platform explorer](audit.md#platform-history) can disclose one explicitly
supported source family: global `account` events with action
`identity.account_access_saved`. Selecting one reveals the saved account status,
global permission codes and recorded reason. These are historical values from
that journal event; later account edits do not replace them. The source journal
does not record previous values or an account version, so this detail does not
invent either or substitute a current account lookup.

## HTTP and authority

`GET /api/v1/administration/audit-events/{eventId}/account-access`

Requires both **global `audit.read` and `identity.manage`**, in the original and
current actor, with active credentials and applicable MFA. Company scope or either
grant alone is insufficient. The use case owns the shared account guard and global
transaction, obtains current access after the guard, and checks assurance before
and after acquisition. A changed credential, revoked grant during guard waiting,
or MFA expiry during the read cannot publish the detail.

The response has exactly five fields:

| Field | Historical meaning |
| --- | --- |
| `id` | Selected audit event UUID |
| `accountId` | Account affected by the change |
| `active` | Saved active flag |
| `permissions` | Saved global permission codes; at most 32 distinct values |
| `reason` | Recorded reason, nullable, at most 1,000 Unicode code points |

Each permission is a lower-case code matching `[a-z][a-z0-9_.:-]{0,79}`. Empty
permissions and absent reasons are valid historical values. The response uses
`no-store`. Missing, unsupported or malformed historical sources return the same
safe `404 audit_detail_not_found`; clients translate this into English/Indonesian.
No arbitrary payload, employee profile, account credential or name is returned.
The existing metadata endpoint still selects only its original seven columns.

## Storage and transaction boundaries

V100 adds `platform_account_access_audit`, a six-column immutable projection that
also retains the source actor for insertion validation. It references the V99
metadata event. Journal writers remain locked across backfill and replacement of
the existing capture function, which inserts metadata before typed details in the
same transaction. Failed business transactions leave neither projection behind.

Only a global account-access source with string `active` equal to `true` or `false`
and a valid permission CSV is projected. Parsing caps the encoded value at 4,096
bytes and the list at 32 codes before bounded validation. Unsupported/malformed
records stay available as metadata without a detail row. The invoker validation
trigger requires exact source ID, actor, affected account, saved status, permissions
and reason. Extra journal keys are never copied.

Forced RLS requires global scope and both current active-account grants for reads.
Capture retains existing own-actor journal admission even while a company is
selected; this grants no company-scope read. There is no runtime update/delete
policy, and privileged mutations also fail the immutable trigger. The original
`audit_entries` RLS is unchanged: reading the typed projection does not expose
another actor's raw journal payload. No new encrypted record family is introduced.

## Dashboard ownership

The detail is part of the existing Fluent drawer at `/platform/audit`. It loads
only for this source family when the current platform snapshot has both grants.
An independent controller owns one detail and one abortable request. Closing,
hiding, changing the account or relevant permissions, refreshing the page, or
navigating discards it. Superseded and late results cannot restore private state.
Retry is explicit, with no persistent cache, polling or accumulated event map.

The strict parser rejects extra fields, duplicate/oversized/invalid permission
lists and reasons over the Unicode limit. The repository also matches both event
and affected-account IDs against the selected metadata before publishing immutable
values. Historical text is rendered as text, including markup-like reasons.

## Reproduction and validation — 29 October 2026

The real three-account acceptance is tracked in
`apps/dashboard/integration-worker/platform-account-audit.spec.ts`. Build both
application JARs, then use the [handoff environment](agent-handoff.md#commands-and-environment):

```sh
cd apps/dashboard
npx playwright test --config playwright.worker-integration.config.ts integration-worker/platform-account-audit.spec.ts
```

The isolated API/PostgreSQL/worker/Chromium workflow passed in 7.9 seconds
(38.6 with setup). An independent MFA operator saves access through the real API;
a reader with both grants sees the operator's reason and exact saved values.
Another MFA reader with only audit permission sees metadata and receives 403 for
the detail. After a second access change, the original event is unchanged and the
new event has its own inactive/empty-permission snapshot. Revoking the detail
reader's management grant invalidates its session; refresh clears the view and
returns to sign-in. Owned services and private fixture files are removed.

Migration coverage exercises V99 backfill, current RLS, unchanged raw-journal
isolation, source validation, immutable writes, rollback, company denial and either
grant removal. Repository/API tests cover bounds, detached values, cancellation,
original credentials, guard-wait revocation, pre/post-read MFA and OpenAPI fields.
Across focused runs, 206 distinct backend cases passed: one migration, fourteen
administration data, 97 audit/identity/API-contract and all 94 worker tests. Both
JARs and architecture/format checks pass. Dashboard checks passed 3,956 tests in
347 files (66.13 seconds), architecture, Biome and TypeScript. Eleven distinct
focused Chromium cases cover company/global metadata and the new typed panel,
including explicit retry, focus ownership and English/Indonesian narrow layouts.
The final two typed-panel cases passed in 7.2 seconds including build/setup; both
scrolled screenshots were inspected.

Full isolated recovery at schema 100 verified two retained encrypted records,
old/current keys, 104 objects/2,098,795 bytes, source independence and rejection of
a wrong retired key. The fixture now captures a fictional account-access journal
through normal triggers and independently restores all six typed columns exactly.
The verifier ceiling includes schema 100 after review of its encryption contract.
Twenty recovery-tool cases pass. No live deployment or Mobile/Starter port is
part of this HRIS administration feature.

Local supplementary evidence: `.work/account-audit-{initial,backend,identity-final,migration-final}.log`,
`.work/account-audit-ui-check-2.log`, `.work/account-audit-browser{,-final}.log`,
`.work/account-audit-real-browser.log`, `.work/account-audit-recovery-set.log`.
The initial binding fixture needed its new presentation child mocked for jsdom's
Fluent/Tabster import limitation; actual rendering is covered in Chromium. The
first browser script tried to change shell language while a modal drawer owned
focus; closing it before changing language fixed the test. An initial verification
command used a nonexistent task; the corrected task is `recoverySetCheck`.
