# Native chat notifications

## Delivery transport implemented — 1 November 2026

`PushMessage.delivery` explicitly selects `Background` (default) or `Visible`.
Both retain bounded data-only FCM fields and target validation. The default TTL
remains 1–300 seconds; explicit recoverable retention is described below.
Android uses NORMAL/HIGH respectively and always lets the admitted application
post the notification. No global/Android `notification` field bypasses that flow.
Apple background remains content-available/priority 5. Visible provides a generic
alert/priority 10, mutable-content and default sound; it contains no chat plaintext.
Fallback title/body are bounded to 80/200 UTF-16 units, 240/600 UTF-8 bytes and
exclude controls/blank values. These limits do not enforce semantic privacy:
producers must supply fixed generic copy, never decrypted message text.

An optional 1–64 ASCII collapse identity maps to both platforms; absent means
no collapse. Existing inbox delivery explicitly uses `Background("hris_inbox")`,
preserving Android grouping and adding the same identity on Apple. Coalescing is
a recovery hint, not durable delivery or a read receipt. Current inbox recovery
remains authoritative. Payloads and delivery objects redact their diagnostics.

[Evidence](../measurements/chat-push-delivery-profile-2026-11-01.json): 15 push,
13 communications policy and eight actual worker cases, format/architecture pass
in 58s Gradle / 59.222s wall. A preceding 14s compilation failure and its explicit
map-type fix are retained. This does not prove live FCM, a chat producer, Android
receive/reply, an Apple notification extension or incoming-call handling.

## Matrix pusher transport implemented — 1 November 2026

`CollaborationMatrixPusherRepository` exposes set/remove for server-issued keys
matching `hriscp1_` plus 64 lowercase hex characters. The adapter fixes app ID
`dev.fajar.hris.chat`, generic display names, `append=true`, and `event_id_only`.
Operator configuration accepts only HTTPS with the exact push-gateway path, no
credentials/query/fragment and a bounded address. No native FCM token, provider
route or arbitrary notification content is accepted by this contract. Removal
uses the same app/key with explicit JSON null kind. Both paths use the existing
owned transport, with 8 KiB request/response limits, bounded deadline/capacity,
strict empty-object success, no redirects and no automatic send retries.

[Actual-provider and transport evidence](../measurements/chat-matrix-pusher-transport-2026-11-01.json):
29 cases/three suites pass, format/architecture pass (52s Gradle). Two fixture
mapping failures from the first run are retained; conversion to a Kotlin list
fixed Jackson's whole-node map shadowing. Actual Synapse v1.164.1 registration is
repeatable, survives restart, keeps a second key after repeated first-key removal,
and survives real five-second access-token expiry followed by same-device renewal.
Synapse's current implementation associates new pushers with device IDs. This
proves retained registration, not callback delivery or mobile notification display.
No DI/controller/schema change or live deployment has occurred for this adapter.

## Encrypted device binding implemented — 1 November 2026

Schema 115 retains one unpredictable `hriscp1_` key per immutable company/account/
native-session/provider-device binding. Only its SHA-256 lookup and AES-GCM envelope
are stored. AAD binds the immutable IDs, reservation microsecond timestamp and
key hash. The scoped repository returns a redacted key wrapper; metadata lookup
runs in its own transaction and identifies the next locks, without authorizing
chat. RLS scopes ordinary reads/writes to the account and selected company.

Reservation commits before provider I/O. The one 0-to-1 publication transition
preserves the original key/ciphertext; concurrent or lost responses reuse that
identity. SQL rejects replacement/deletion, foreign or missing parent bindings,
inactive devices and publication after revocation. Parent purging retains the
identity; callback admission must still reject that parent. The recovery tool
now authenticates this envelope at schema 115 while retaining earlier-schema
queries and rejecting unreviewed schema 116. Real dump/restore verifies both old
and current keyring material and the actual recovery SQL.

The key stays stable when a native FCM registration/token rotates. Actual delivery
must reread and fence that registration's current version; no FCM token is copied
into Synapse. This refines the earlier proposal to rotate the capability on every
registration change: stable device identity avoids duplicate provider pushers and
out-of-order deletion when notifications are re-enabled. A different device gets
a different key. Temporary push disablement or access loss must suppress delivery;
only permanent device/parent retirement may remove its retained pusher.

[Storage/recovery evidence](../measurements/chat-push-binding-storage-2026-11-01.json)
records full database/domain/data and actual API/worker regression checks plus 33
Python recovery cases. The first corruption-fixture failure and correction are
retained. No live schema change, DI/controller or admitted callback is included.

## Managed activation implemented — 1 November 2026

`POST /_matrix/client/unstable/dev.fajar.hris/pusher` is an empty-body vendor
extension using only the admitted Matrix gateway bearer. Cookies/native access
bearers, duplicate authorization, queries, bodies and alternate protocol routes
are rejected. The response is only `{}` with `Cache-Control: no-store`; callers
cannot select an app, pushkey, callback or native token. Standard `/pushers/set`
remains blocked. Activation uses the existing current interactive account/company/
member/native-session/device/MFA/client-policy exchange.

Preparation reserves the original encrypted key and journal before provider I/O.
The current enabled, unexpired, matching-platform native registration is captured
and revalidated around the unlocked external call and final publication. Its
expiry also bounds the provider deadline. Lost responses/interruption retain the
same reservation; parallel callers share that identity and publish once. Token
rotation preserves the pusher key, while a changed registration during a call
prevents publication. Current denial overrides provider failure; post-journal
expiry rolls publication back. No plaintext target is returned or logged.

`HRIS_COLLABORATION_PUSH_ENABLED` defaults false. Enabling it alongside the existing
collaboration/Matrix flags requires `HRIS_MATRIX_PUSH_GATEWAY_URL` with the fixed
HTTPS callback contract. The disabled repository performs no provider I/O. Keep
the flag off until private callback ingress, safe provider logging and durable
recovery/delivery are deployed.

[Admission evidence](../measurements/chat-pusher-admission-2026-11-01.json): 40 domain,
121 data, 38 actual API and six actual worker cases pass, with format/architecture
in 222s Gradle / 222.990s wall and 13 unchanged inputs. Earlier fixture/compiler
failures and the seven-case focused success are retained. Both disabled API and
enabled worker dependency graphs are exercised. This proves outgoing activation,
not background callback authority, FCM delivery or Mobile receive/reply.

## Background hint policy and decoder implemented — 1 November 2026

`collaborationPushTarget` resolves a narrow hint decision from current guarded
inputs. Short native/gateway access expiry and MFA age do not stop opaque pushes;
ordinary interactive admission still rejects those expired/missing proofs. Hints
require the current active account/company/member/chat permission, unexpired and
unrevoked native parent with matching credential version, exact active bound
device, current enabled native registration and current client availability.
Gateway metadata supplies the known app build, without treating its access token
as background authority. The parent, registration, gateway lifetime, scheduled
client policy and 30-second ceiling bound each prepared result.

A permanently missing/revoked/expired parent or retired device permits rejection
of its retained pusher. Temporary registration/access/client unavailability merely
suppresses delivery, allowing re-enable with the same identity. Inconsistent
foreign projections also suppress without deleting another owner's key. Reserved
binding version zero can identify early callbacks. Version-fenced revalidation
allows its sole publication transition while rejecting late token/session/device/
gateway changes and expired/backwards observations. Event hints separately require
exact immutable DIRECT ownership; this is not removable group/context admission.

`matrixPushNotification` accepts only 8 KiB and 32 distinct fixed-app opaque keys,
with bounded JSON depth/strings/numbers and duplicate/trailing-token rejection.
Content, names, arbitrary device data/URL/tweaks and malformed IDs are rejected.
Counts/priority are validated, then discarded: the callback exposes only optional
room/event IDs and redacted keys. A count-only hint is silent. Synapse's legacy
badge `id=""`, `type=null`, `sender=""` sentinels are accepted only without an event;
nonempty variants are rejected. The eventual HTTP owner must also limit stream
size/time, concurrency and private ingress before invoking the decoder.

[Policy/decoder evidence](../measurements/chat-push-hint-policy-2026-11-01.json): 49
domain and five delivery cases, format/architecture pass in 30s / 30.517s wall.
The first fixture security-version mismatch is retained and corrected without
changing production policy. Payload shapes match inspected Synapse v1.164.1 source;
actual callback transport, durable reception and delivery are still pending.

## Provider log redaction proved — 1 November 2026

[Deployable configuration](../../deploy/synapse/README.md) uses a single final-output
formatter that redacts the opaque pusher namespace after substitutions, exception
chains, cached exception text and stack rendering. It copies the record before
formatting, caps rendered UTF-8 output at 16 KiB, and emits a fixed diagnostic on
formatting failure instead of Python logging's raw-argument fallback. Termination
still propagates. This is not an arbitrary-secret scrubber; request bodies, native
tokens, bearer headers and plaintext remain excluded from diagnostics.

[Evidence](../measurements/chat-push-provider-logging-2026-11-01.json): the exact
module/config load in both actual Synapse v1.164.1 pusher cases, including retained
registration after restart and five-second provider credential expiry/renewal.
Bounded final snapshots positively contain redacted startup/removal records and
no raw pusher namespace. Snapshot ownership closes on all exits, with a five-second,
1-MiB/4096-line evidence bound. Seven Python regression cases run in root `check`.
47s Gradle / 48.097s wall, format/architecture pass. The first passing live-consumer
proof and the compiler fix for the stronger restart-safe snapshot are retained.
No live provider configuration or callback deployment has occurred.

## Durable coalesced hints implemented — 1 November 2026

Schema 116 adds one wake head per retained immutable device binding, without
message plaintext or a new secret envelope. Each head stores monotonic received/
acknowledged revisions, at most 64 SHA-256 event fingerprints, the latest visible
room/event identity, bounded times and worker lease metadata. Exact recent retries
do not advance or restart exhausted work. Older-than-window retries can wake again;
Matrix/client original identity must deduplicate notification display. This is
at-least-once recovery, not exactly-once FCM delivery.

Callbacks arriving during a lease advance the received revision while preserving
the lease. Finishing only acknowledges the claimed revision, leaving newer work
pending. Quiet hints never overwrite visible event identity; a consumer must compare
`visibleRevision` with `deliveredRevision` and its age before presenting an alert.
An untouched pending quiet hint coalesces. A new quiet hint after an attempted or
exhausted revision can recover, including when a worker uses a smaller retry budget.

Ordinary RLS requires the exact company/account. Only workers can claim or finish
delivery. Claims return at most eight heads with a global maximum of 16 active
leases; lease duration is 1–60 seconds and attempts are bounded to eight per revision.
Late/foreign leases cannot acknowledge a replacement claim. New work releases stale
backoff without losing an active lease. These are bounded pilot settings, not
5,000-employee throughput acceptance. Lifetime storage allows 1–86,400 seconds;
the current FCM transport remains at 300 seconds and has no chat producer yet.
One small head remains per retained device; this is not a historical event feed.
Startup/full Matrix sync remains necessary after lost/coalesced hints.

[Evidence](../measurements/chat-push-queue-2026-11-01.json): 30 database, 49 domain,
137 selected data, 38 actual API and six actual worker cases, plus 24 Python
recovery cases and format/architecture pass (357s Gradle / 357.972s wall, nine
unchanged inputs). Eleven queue cases cover concurrent callbacks/claims, RLS,
actual expiry, retry/rollback and restore. A pre-existing encrypted binding survives
115-to-116 migration; actual dump/restore retains an in-flight lease plus newer
callback, original key, normal RLS and correct next-worker continuation. Recovery
now accepts 116 with the same envelope projection as 115, and rejects unreviewed
117. The earlier compiler fixture issue and focused success are retained.

This increment has no DI/controller/dispatch adapter or live migration. Current
background admission and room ownership must precede append; queue metadata never
grants authority. Wire the existing policy/decoder into the private callback and
worker before enabling delivery. Keep operation deadlines separate from FCM
store-and-forward lifetime, and implement explicit offline notification recovery.

## Following implementation boundaries

Use Matrix v1.16 `event_id_only` HTTP pushers. The server fixes app identity and a
HTTPS callback ending in `/_matrix/push/v1/notify`. Native clients cannot choose a
callback URL or send FCM tokens to Synapse. Bind an unpredictable pushkey to the
company/account/native session/provider device; callbacks identify that binding
before reacquiring current access, membership, device and room authority.
Matrix callback authentication has no standard bearer contract, so a scoped
capability and restricted ingress are required. Logs must never expose pushkeys.

Persist bounded callback recovery/outbox state before acknowledging provider
retries. Duplicate events and coalesced hints must recover through durable state;
a callback is neither authorization nor message content. Delivery preparation,
network send and version-fenced acknowledgment stay separate. Native push token
rotation, disablement and account changes must beat late delivery/retirement.
Inline replies use the existing encrypted message queue with durable action IDs;
no SDK work or plaintext WorkManager payload belongs in a notification receiver.

Mobile's current collaboration owner follows the selected company. Do not switch
workspace invisibly for push or claim all-company background support. That needs
an explicit bounded background ownership model and separate tests. Rich previews,
privacy, mute/read controls, Android reply actions, Apple NSE/APNs and browser
service workers remain outstanding. VoIP needs its own PushKit/CallKit path.
Port generic primitives to Starter only after Mobile validation. CHAT-13 and all
54 feature groups remain unaccepted; continue the complete P0–P8 plan.

References: [Matrix push gateway](https://spec.matrix.org/v1.16/push-gateway-api/),
[pushers](https://spec.matrix.org/v1.16/client-server-api/#push-notifications).

## Separate dispatch and provider retention implemented — 1 November 2026

`PushMessage.dispatchDeadline` defaults to the existing expiry. It must not exceed
that expiry and allows at most five minutes of remaining operation budget. The
repository checks it before credentials, after OAuth, around FCM and after outcome
mapping. FCM request plus full-body timeout is clamped to the remaining budget.
An expired result cannot publish ACCEPTED or UNREGISTERED to its caller. OAuth
remains independently bounded to10s and can finish after a shorter caller budget;
it then cannot begin FCM. Cancellation and bounded response handling are preserved.

`PushRetention.SHORT_LIVED` remains the default300-second storage ceiling.
Explicit `RECOVERABLE` permits up to86400 seconds and therefore requires a separate
short dispatch deadline for long hints. Android TTL/APNs expiry use provider
storage lifetime after credential acquisition; they never inherit a30-second
admission budget. Retention is a ceiling, not a delivery guarantee or authority to
read content. Current producer/client admission and periodic/reconnect/full sync
remain mandatory. This change does not enable a chat producer or live FCM send.

[Evidence](../measurements/chat-push-dispatch-deadline-2026-11-01.json):20 push,
13 communications policy, nine actual API and eight actual worker cases pass,
format/architecture pass. Real signed OAuth succeeds after dispatch expiry without
starting FCM; a real stalled response body obeys the short dispatch timeout. Final
integration76s /76.499s wall with nine unchanged inputs reuses focused32s push
reports. The intermediate incorrect Gradle module path is retained; source unchanged.

## Durable callback admission implemented — 1 November 2026

`HandleCollaborationPush.accept` validates1–32 distinct fixed-format capabilities
and a maximum30-second caller budget. The use case owns a separate zero-account
lookup transaction because the raw source installs transaction-local lookup scope.
It then starts each exact company/account transaction and reacquires policy,
company, membership, account, native parent, device, gateway and push-binding guards.
No lookup projection authorizes delivery. Current narrow background policy is
reused; short access/MFA expiry still denies interactive content separately.

Unknown/permanently retired keys are returned for removal. Temporary authority,
registration or client unavailability suppresses without deleting a valid key.
An event requires the actual immutable DIRECT ownership projection. Quiet hints
need no room. Valid hints append a24h coalesced wake with caller/target deadline and
clock checks after writes; failed Result/interruption rolls back. Each key commits
independently, so a failed multi-key request can safely retry its completed prefix.
This accepts an early version-zero pusher callback before activation publication.

[Evidence](../measurements/chat-push-callback-admission-2026-11-01.json):ten selected
actual SQL cases plus49 domain policies, format/architecture pass in54s /54.460s
wall. The first missing lookup transaction and subsequent nine-case pass are
retained. No private HTTP route, DI, worker send or live provider acceptance yet.

## Restricted callback HTTP implemented — 1 November 2026

`HRIS_COLLABORATION_PUSH_CALLBACK_ENABLED` defaults false independently of outgoing
activation. Enabling it requires collaboration/Matrix/push and one to32 numeric
socket peers/CIDRs in `HRIS_MATRIX_PUSH_CALLBACK_SOURCES`. Hostnames, DNS, scoped
addresses and ambiguous IPv4 forms cannot authorize. `server.forward-headers-strategy`
must remain NONE. A private TLS listener and exclusion from public API ingress
are still required; the socket peer allowlist is not a replacement for TLS.

Only exact POST `/_matrix/push/v1/notify` enters the controller. Source validation
precedes input acquisition. Queries, compressed bodies and non-JSON are rejected.
Actual streamed data is capped8KiB with2s elapsed checks and four body readers.
The enabled connector validates its effective body socket timeout at1–5000ms
without altering upload behavior. Tomcat's marked ERROR dispatch gets a narrow
callback-only handler using server-populated attributes, the same source allowlist
and fixed bounded output. An actual stalled read now produces408, not native401.

Four owned FutureTask executions use a10s monotonic caller limit and no waiting
queue. Timeout, caller interruption and shutdown cancel that task; a still-running
bounded JDBC operation keeps consuming its execution slot. Domain cancellation
propagates. Success contains only rejected unknown/permanent keys and no-store;
current durable per-key transactions complete before acknowledgment. No callback
body/capability is logged. Queue/handler DI composes in both application roles.

[Evidence](../measurements/chat-push-private-ingress-2026-11-01.json):14 delivery,
42 actual API and six actual worker cases, format/architecture pass in116s /
117.140s wall. Spoofed forwarding headers from127.0.0.2 cannot bypass the source
check even with a cookie/bearer and no body bytes sent. Earlier compiler and actual
Tomcat error-dispatch failures are retained. Private TLS/provider end-to-end,
FCM worker delivery, client receive/reply and live rollout remain separate work.

## Actual Synapse callback and private TLS proved — 1 November 2026

The [deployable Nginx listener and public denial](../../deploy/synapse/README.md)
load unchanged in a pinned Nginx1.30.6/Synapse1.164.1 fixture. An owned ephemeral CA
is trusted through ordinary OpenSSL/Java verification; an untrusted Java client
rejects it. Public callback access returns403, private TLS200 and oversized input413.
Stopping the proxy produces actual Synapse failing_since; restarting it lets the
provider retry the exact synthetic encrypted event through API/RLS into the durable
queue and clears the provider failure. Revoking the native HRIS parent makes a
subsequent callback remove the retained Synapse pusher while preserving the prior
queued event. Scoped room publication metadata is fixture SQL; this case does not
claim native SDK cryptography, the publication worker or FCM/device receipt.

[Evidence](../measurements/chat-push-provider-callback-2026-11-01.json):one actual
provider/API case,39.909s suite/17.622s method, format/architecture pass;220s Gradle /
221.378s wall, six unchanged inputs. First15s test compile needed PKIX certificate
builders in addition to the existing crypto provider; test-only bcpkix uses the
existing1.86 version. All owned containers are reaped. No live provider rollout or
capacity acceptance; queue/FCM worker and Mobile receive/reply are the next work.


## Current queued delivery boundary

`HandleCollaborationPush.claim/deliver` now claims a bounded worker lease, prepares
under ordered current authority/owned-room guards, acquires the current native-token
version, sends outside the transaction and reacquires the complete lease/target
before acknowledgment. Dispatch is capped at20 seconds, separately from the explicit
RECOVERABLE provider storage lifetime of up to24 hours. Payload contains bounded
opaque account/company/parent/device/revision/event identifiers and a fixed generic
visible fallback, or only a quiet wake. Never include message text, names, ciphertext,
push keys or FCM tokens in its data map.

An old lease cannot borrow a newer callback's visible identity or acknowledge that
new revision. Token rotation defeats late UNREGISTERED; current retirement and ACK
are atomic, including failed-result/expiry/interruption rollback. Lost ACK may
repeat the same wake ID. At most eight attempts use bounded5–300-second backoff;
new callbacks recover independently after exhaustion. ACK means processed queue
work, not delivery to a device or message-read confirmation. The single `hris_chat`
collapse key requires all-owned-company recovery without changing selected workspace.

[Evidence](../measurements/chat-push-delivery-2026-11-02.json):55 domain,138 full
registration SQL,42 actual API and6 actual worker cases, format/architecture pass
in393s Gradle /393.482s wall with seven unchanged inputs. The retry test waits the
first real5 seconds; later delays are captured but compressed through the real fenced
defer transition. First failure was the fixture's forbidden arbitrary schedule
rewrite; second was KDoc formatting after all18 focused SQL cases passed. Guards
and trigger remain unchanged. The sender is a fixture PushRepository in these cases.

## Automatic delivery worker

`CollaborationPushWorker` owns four delivery threads, no waiting task queue and two
control threads. It polls every 250 ms for available capacity only. Each task has
an owned 20-second cancellation timer. Cancellation does not release an execution
slot until its underlying operation exits. Shutdown cancels polling, interrupts
work and closes both pools; durable leases remain recoverable after process death.

The worker requires explicit `HRIS_COLLABORATION_PUSH_DELIVERY_ENABLED=true` plus
FCM, collaboration, Matrix and managed push enabled. Global worker disablement also
prevents startup. Callback ingress has an independent flag, so already-retained
work can drain while ingress is off. Defaults keep delivery disabled. Capacity and
timeouts are bounded pilot settings, not a throughput claim.

[Evidence](../measurements/chat-push-worker-2026-11-02.json): 11 tests pass, including
four loop/lifecycle cases and seven actual application-context configurations in
one context test. The enabled context claims the real PostgreSQL queue under its
worker role. All disabled configurations do no chat push work and every context
terminates its executors. Six provisioning regressions and format/architecture
pass; 119s Gradle / 119.725s wall, six unchanged inputs. Empty-queue contexts perform
no FCM send; transport and guarded delivery have separate evidence above.

Mobile receive/decrypt/reply and all-owned-company reconnect/periodic/full recovery
remain required before activation. No live rollout or complete CHAT-13 acceptance
is claimed here.
