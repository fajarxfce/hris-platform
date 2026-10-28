# Integration configuration review

Platform administrators can review the API's startup enablement flags at
`/platform/integrations`. The view works without a company assignment. Its fixed
six-row catalogue identifies which API integrations were enabled when the process
started; connection health, worker configuration and successful provider delivery
require separate verification. Changing deployment settings requires the normal
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

## Validation

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
