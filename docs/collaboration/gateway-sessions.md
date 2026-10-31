# Native Matrix gateway

Schema 109 stores one HRIS gateway credential head per company/account/device.
The public access and refresh values are separate 256-bit opaque credentials;
they are neither native HRIS tokens nor internal Synapse tokens. Only their
SHA-256 hashes and one encrypted lost-response response are persisted. Participant
E2EE private keys never enter this store.

The retained identity includes the native parent, platform, client build and
monotonic version. New heads require the exact active device and parent binding.
The parent may later be purged without erasing the credential generation. Version
one million is reserved for retirement after irreversible device revocation;
SQL rejects deletion, resurrection, foreign bindings and stale compare-and-set.

Refresh atomically replaces both hashes and remembers only the immediately prior
refresh hash. Racing rotations produce one winner. That previous hash can replay
only the winning generation's encrypted response; another rotation removes it.
A losing or older request never revokes the winning device or deletes its keys.
Explicit bootstrap can update the client build while retaining the same Matrix
device. Its operation identifier selects only the current bootstrap response.

The response cache has a maximum 60-second lifetime; access lifetime is bounded
at 15 minutes and the hard deadline by both the parent and 31 days. Admission use
cases must enforce their shorter operational defaults, replay deadline, current
native access/MFA/company/permission/client policy, and admission again after any
external I/O. A token lookup only identifies a record. It runs in a separate
transaction using a transaction-local hash capability; it never authorizes a
provider request by itself. Native and browser parent admission remain distinct.

AES-256-GCM uses the existing separately retained identity keyring. Its context is
`hris:matrix-gateway:v1:` followed by the SHA-256 of canonical colon-separated
company/account/device/head/parent/platform/build/version, creation/rotation/hard/
access deadlines in Unix microseconds, both token hashes, replay kind, bootstrap
operation or previous refresh hash, and replay deadline. Every field is delimiter
safe and microsecond normalized. The context hash satisfies the existing 256-byte
AAD limit. Recovery selects this projection only for schema 109 or later; actual
PostgreSQL dump/restore verifies both old and current keys and the independent
Python/SQL projection against the Kotlin encryption context.

Expired cache cleanup locks at most 100 rows with SKIP LOCKED. It erases encrypted
response, prior hash, kind and operation without changing the active token hashes,
head version or device. The expired deadline stays as nonsecret metadata so the
restricted maintenance role can see the updated row under PostgreSQL RLS. A new
rotation replaces that deadline and hides its live replay from the maintenance
role. The trigger forbids changing other facts during this cleanup and takes no
reverse device lock. Application clock drift cannot clear a cache before the
database clock says it has expired.

The original storage increment is now wired to bootstrap/refresh and bounded
maintenance as described below. SDK content synchronization, product chat and
browser parents still need implementation. Internal provider credentials remain
separate from public gateway credentials.

## Native bootstrap and standard refresh admission

`BootstrapNativeCollaborationGateway` now owns native issuance under operation →
company policy → company → membership → account → native parent → device → gateway
head guards. It requires both service/provider enablement, native Android/iOS build
metadata, the observed active device version and the observed gateway version
(null for the initial head). The idempotency fingerprint omits parent version so
ordinary native refresh can recover a lost response, but includes platform/build
and the observed gateway/device versions. Receipts hold only head ID/version.
A replay can return only the currently winning encrypted pair within 30 seconds;
an expired/superseded receipt never starts another generation.

`RefreshNativeCollaborationGateway` accepts the standard refresh token without a
client operation UUID. An isolated transaction-local token lookup identifies the
owner; a new scoped transaction locks current HRIS authority before the head.
After serialization it can recover the latest encrypted response, including when
another request rotated between lookup and lock. Older requests are rejected;
none revokes a device, calls Synapse logout, or erases an owned store.

Operational access lifetime is five minutes, response replay 30 seconds and the
minimum rotation interval ten seconds. A current refresh token can renew expired
gateway access while its native HRIS access remains live. It cannot extend native
HRIS access, MFA, company availability or permission. After an ordinary native
refresh, the same gateway/device identity can continue. The hard gateway deadline
is the earlier of the parent deadline and 31 days; a custom native lifetime beyond
31 days requires a new HRIS parent after that gateway deadline. The normal native
session lifetime is 30 days.

Both new issuance and replay reacquire current company policy/admission at
completion. The final response policy checks the same instant against admission,
head identity/version, access expiry and the authenticated replay deadline.
Credential writes, bootstrap receipt and audit/outbox roll back together on final
authority failure. Refresh records a safe version-only journal entry; it does not
create operation receipts or unbounded consumed-token history.

The use cases were accepted separately before the HTTP/worker integration below.
Those transaction tests do not imply SDK ownership, provider proxy or browser
admission acceptance.

## HTTP and worker integration

The shared module configuration now composes the gateway store, native bootstrap,
standard refresh and maintenance use cases. Native setup status includes only
head ID/version, captured build and expiry/revocation metadata; neither token nor
its hash/ciphertext is exposed in status. `POST` on the native-device route's
`/bootstrap` suffix requires native transport, client build, idempotency key and
observed device/gateway versions. Its no-store response contains public Matrix
user/device identity plus the scoped opaque gateway pair. Clients use their known
HRIS API origin; no untrusted Host header selects a homeserver URL.

`POST /_matrix/client/v3/refresh` accepts only the standard `refresh_token` JSON
property. The body is limited to 2 KiB including chunked requests; duplicate keys,
trailing values, excessive nesting, unknown properties and non-JSON content are
rejected. Stream reads check a two-second budget and inherit the server's five-
second socket timeout; the check does not claim to interrupt an already blocked
read at exactly two seconds. Replies use `access_token`, `refresh_token` and
`expires_in_ms`; unknown/expired authorization uses Matrix `M_UNKNOWN_TOKEN`
with `soft_logout`, permission failure uses `M_FORBIDDEN`, and rotation bounds use
`M_LIMIT_EXCEEDED` with `retry_after_ms`. Responses and token DTO strings are safe
for caching/diagnostics: no-store and explicit redacted representations.

An ordered stateless Matrix security chain bypasses native-bearer/cookie parsing.
POST refresh, the fixed GET identity operation described below and the typed
[key upload](device-key-upload.md), [query](device-key-query.md) and
[claim](device-key-claim.md) routes and fixed
[encrypted to-device PUT](to-device-envelopes.md) are permitted. All other `/_matrix/**` and `/_synapse/**` routes, including login, logout, admin,
federation and sync, are denied. This is not yet a usable SDK sync/content gateway.

The worker owns one fixed-delay cleanup task, one timer thread and at most one
100-row transaction per pass. It defaults to five seconds and remains eligible
when collaboration/provider flags are disabled, so old response caches can drain.
`hris.worker.enabled=false` or `HRIS_COLLABORATION_GATEWAY_CLEANUP_ENABLED=false`
disables allocation. `HRIS_COLLABORATION_GATEWAY_CLEANUP_INTERVAL_SECONDS` adjusts
the interval within one second to one hour. Shutdown cancels the task, interrupts
in-flight work, rolls back incomplete cleanup and terminates the timer; no
per-session jobs or unbounded draining loop are created.

The [provider transaction-retention gate](provider-transaction-retention.md) is
required before offline send acceptance. Gateway credential replay and Matrix
message deduplication are separate contracts.

## Protected provider identity request

`GET /_matrix/client/v3/account/whoami` accepts exactly one Authorization bearer
header containing the HRIS gateway access token. Native HRIS credentials and
browser cookies cannot replace it. Duplicate headers, query parameters and
request bodies (including chunked bodies) are rejected. Only the validated
`user_id`, `device_id` and `is_guest=false` projection is returned, with no-store.
No untrusted URL/path/header is forwarded to the provider.

`AccessNativeCollaborationMatrixDevice` resolves the opaque hash in its own lookup
transaction, then reacquires company policy, company, memberships, account,
native parent, device, gateway head and internal provider credential in order.
Both service flags must be enabled. It uses the head's platform/build and live
parent credential/MFA facts, checks current permissions/expiry and requires the
same head version. The initial admission deadline also clamps to public gateway
access expiry. Neither the token lookup nor an earlier bootstrap grants access.

The fixed private WHOAMI exchange holds no SQL locks. Afterwards every HRIS
authority is reacquired and the captured device/parent/credential versions and
deadlines are checked before returning either success or provider failure.
Foreign identities or guest responses are rejected. A native refresh between
requests preserves the gateway; one that races an in-flight request invalidates
that request's earlier observation. The stale observation returns
`collaboration_admission_changed` as temporary unavailability. Current permission,
MFA and revocation failures keep their own denial classification; an ordinary
refresh must not masquerade as permanent access removal.

An absent or nearly expired internal credential (ten seconds or less remaining)
is renewed through private JWT login outside SQL. Publication repeats live
admission, head and provider-generation checks. Competing candidates publish
one generation; a loser can use that current winner. Otherwise it returns a
failure without overwriting or logging out the winner. Unused candidates expire
privately. Credential publication and its generation-only audit/outbox record
roll back together on final admission failure. No public gateway rotation is
performed as a side effect, and no participant private key is generated here.

The shared private HTTP adapter caps concurrent exchanges at 32 without a waiting
queue. Capacity exhaustion returns a bounded retry hint. A permit covers the
request and bounded response read, and is released on failure, timeout or
interruption. Login, identity and irreversible device logout share that cap.
The fixed origin, no-redirect policy, five-second maximum exchange and 64 KiB
response cap remain in force for those operations. Bulk key query/claim use a
one-MiB response cap through the same owner. These are resource bounds, not
capacity results.

Verification includes actual pinned Synapse identity acquisition/reuse and
PostgreSQL authority/rotation races, plus an owned HTTP provider in the actual
API graph. The HTTP fixture simulates the earlier activation transition; it does
not replace the separate worker/Synapse provisioning proof. See the
[identity evidence](../measurements/collaboration-gateway-identity-2026-10-31.json)
for exact tests, failures corrected and scope. The later typed key routes have
their own evidence above; sync, room/content and browser-parent admission remain
separate gates. A private provider token
rejected before its stored expiry is reported as provider unavailability; it is
not silently retried or used to delete/recreate a device.
