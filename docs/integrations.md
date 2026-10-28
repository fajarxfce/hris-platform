# Integration configuration and worker reports

Platform administrators can review the API's startup enablement flags at
`/platform/integrations`. The view works without a company assignment. Its fixed
six-row catalogue identifies which API integrations were enabled when the process
started. A separate worker table shows reports from actual worker processes;
connection health and successful provider delivery require separate verification.
Changing deployment settings requires the normal
application restart/review procedure; this screen is read-only.

| Integration | Startup flag |
| --- | --- |
| Firebase Authentication | `HRIS_FIREBASE_AUTH_ENABLED` |
| Single sign-on through OIDC | `HRIS_OIDC_ENABLED` |
| SMTP email | `HRIS_MAIL_ENABLED` |
| FCM push notifications | `HRIS_FCM_ENABLED` |
| Private S3 object storage | `HRIS_STORAGE_ENABLED` |
| ClamAV document scanning | `HRIS_DOCUMENT_SCANNER_ENABLED` |

`GET /api/v1/administration/integrations/configuration` returns `runtime: "API"`,
an observation timestamp and exactly six `{kind, enabled}` entries. No provider
address, project/bucket identifier, credential path or secret is acquired for this
projection. DI binds the explicit boolean flags to a detached immutable repository
snapshot; request handling does not inspect arbitrary environment properties or
contact an external service. The timestamp records this review, not a health check.

The endpoint requires original and current global `identity.manage`. Company
`identity.manage` or `settings.manage` does not grant access. The use case takes
the caller account's shared guard and checks active credentials, original/current
permissions and current MFA before acquisition. Assurance is checked again before
returning the observation. No business records, provider messages or operational
jobs are created by a review.

The dashboard has strict response validation and one controller per account. A
refresh clears old flags before acquisition. Hidden workspaces, disposal, account
replacement and revoked authority cancel obsolete reads and discard observations;
late success/failure cannot repopulate a previous controller lifetime. Restoring
admission reacquires the configuration. There is no polling timer or persistent
configuration cache. The view uses English/Indonesian copy and the shared portal
header/resource table, with a UTC observation time and narrow-screen layout.

## Running worker observations

`GET /api/v1/administration/integrations/workers` returns a database observation
time and at most 64 process reports. Each report contains a random process UUID,
first/last publication times, `RECENT`/`STALE` status and exactly four startup flags:
`MAIL`, `PUSH`, `STORAGE` and `DOCUMENT_SCANNER`. The flags come from the worker's
actual bound JVM configuration; they are not inferred from the API or Compose.
No hostname, PID, provider address/identifier, credential path or secret is stored
or returned. In particular, worker FCM can be enabled while API FCM is disabled.

Each enabled worker owns one process identity and a fixed-delay 30-second
publication task. A global advisory transaction guard serializes pruning and
publication. The use case uses database time, retains reports for five minutes,
and refuses a new process when 64 unexpired reports already exist. Only the worker
database capability can insert/update/delete observations. First publication,
identity and flags are immutable; later publication advances only the timestamp.
Failed publication rolls back pruning. Shutdown cancels and joins the owned timer
and leaves the last report to age. A new process has a new identity.

A report is recent only when its last publication is no later than the observed
database time and less than 90 seconds old. Exactly 90 seconds, or a future-dated
publication, is stale. Reports older than five minutes are pruned on subsequent
worker publication; if all workers stop, their stale rows remain visible. An empty
registry means no retained report, not successful worker health. This observation
proves that the reporter could publish through its restricted database connection;
it does not prove every worker loop, queue, provider connection or delivery works.

The read endpoint retains the same original/current global authority, account
guard, credentials and pre/post-acquisition MFA checks as configuration review.
Acquisition and mapping share one safe database boundary. The query returns one
bounded statement snapshot; inconsistent/oversized rows fail instead of becoming
an empty or healthy result. No provider I/O occurs during a dashboard review.

The worker panel owns its request independently of the API panel. Refresh clears
old reports, hides/disposal cancel requests, and account/admission changes discard
late results. Failures remain distinct from absence. English/Indonesian copy
states that status applies at the displayed UTC observation time and asks the
operator to refresh for a new observation. There is no background browser timer
or persistent health cache. The narrow table scrolls within its card.

## Operator review of API and worker wiring

The [deployment runbook](deployment.md#https-secrets-and-service-wiring) includes
`tool/review_deployment.py`, which accepts rendered Compose JSON on stdin and emits
fixed API/worker enablement flags, minimal-wiring checks and safe issue codes.
Provider values, paths and secrets are never output. The worker projection covers
SMTP, FCM, storage and scanning; Firebase/OIDC request handlers belong to the API.
API/worker FCM flags may intentionally differ with `compose.push.yml`.

This is an operator's static review of the selected deployment files. It does not
inspect live JVM property binding or check provider/worker health. The running
reports above supply their own distinct observations. Six redaction/bounds/configuration regressions and actual rendering
of the base, documents and documents-plus-push files passed with fictional values.
Real provider acceptance remains separate work.

## Validation

The worker-report increment passed 183 backend tests: eight Administration domain,
eight Administration data, 73 selected API/PostgreSQL/OpenAPI and the complete
94-test worker graph. Cases cover restricted-role writes, immutable flags,
competing capacity admission, rollback after pruning, database-time expiry,
90-second/future-time classification, shutdown during blocked publication,
minimal projections, company-only denial, revocation while waiting, MFA expiry,
cancellation and explicit recovery. Both JARs, architecture, formatting and the
20 recovery, six deployment and sixteen workload tool tests passed in 3m28s.

The complete dashboard check passed 3,940 tests across 342 files (60.97 seconds),
including 66 Administration cases, plus architecture, Biome and strict TypeScript.
Six focused Chromium cases passed, including worker/API flag differences,
recent/stale/absent reports, failed refresh, localization and a dark 390-pixel
layout. A separate real API/PostgreSQL/worker/browser scenario passed in 41.2
seconds (1.2 minutes with setup). It observed two actual worker publications,
verified bound mail/push flags, displayed a separately seeded stale process,
denied a company administrator, and revoked the reviewer's global authority using
an independent MFA account. Refresh removed the revoked observation and returned
to sign-in. The stale fixture row does not claim an actual process-stop test.
Screenshots were inspected; all owned fixture services and connection files were
removed. No external provider was contacted by that acceptance workflow.

The schema-98 recovery exercise also passed: 104 objects/2,098,795 bytes, two
encrypted records, old/current keys, source independence and wrong-retired-key
rejection. Owned resources were removed. Logs are
`.work/worker-observation-final-checks.log`, `.work/worker-dashboard-checks-2.log`,
`.work/worker-browser-checks.log`, `.work/worker-real-browser-checks.log` and
`.work/worker-recovery-set-checks.log`. Initial checks found missing Swagger
annotations, an untyped timestamp SQL parameter, a Jackson test-iteration mistake
and formatting in ignored native fixture files; these were corrected before the
passing runs. The existing two stylesheet warnings and Vite size advisory remain.

### Earlier API configuration increment

Backend checks passed 184 tests: seven Administration domain, five Administration
data, 82 selected API/PostgreSQL/OpenAPI and the entire 90-test worker graph.
The new configuration tests prove immutable detached flags and binding restricted
to the six named properties. HTTP/runtime checks prove the minimal response,
company-scope rejection, credential revocation, permission loss while waiting,
MFA expiry before/after acquisition, cancellation and explicit reacquisition.
Both application packages, architecture and formatting checks passed.

The entire dashboard suite passed 3,922 tests across 340 files in 69.95 seconds,
including 48 Administration cases. Architecture, Biome and strict TypeScript passed;
the two existing stylesheet warnings and Vite chunk-size advisory remain. Seventeen distinct
Chromium scenarios passed across focused runs, including four new configuration
scenarios. They cover company-only denial before I/O, empty-company platform
access, transient failures, fresh MFA, revoked navigation and Indonesian dark
390-pixel layout. The initial browser run required fixture corrections for the
existing permission message and authenticator enrollment; all four new cases then
passed in 8.5 seconds. Desktop and narrow screenshots were inspected.

A real API/PostgreSQL/Chromium workflow passed in 10.4 seconds (58.3 seconds
including setup). Three independent MFA accounts reviewed actual startup flags,
denied a company administrator and revoked the reviewer's global permission through
the public account-access command. The old session could no longer read the endpoint,
and refreshing removed the table and returned to sign-in. The independent company
session remained denied. The fixture does not contact or validate a live provider.
All owned fixture services and temporary connection files were removed afterward.

Initial local build attempts used Java 17, an incorrect architecture task name,
then a reused Gradle daemon without Docker group access. JDK 21 with an owned
single-use daemon completed the matrix. Source lint also encountered generated S3
browser reports; their dedicated output directories are now excluded consistently
with other browser artifacts. Those corrections are distinct from passing tests.
