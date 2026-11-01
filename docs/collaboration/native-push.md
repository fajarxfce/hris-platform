# Native chat notifications

## Delivery transport implemented — 1 November 2026

`PushMessage.delivery` explicitly selects `Background` (default) or `Visible`.
Both retain bounded data-only FCM fields, target validation and 1–300 second TTL.
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
