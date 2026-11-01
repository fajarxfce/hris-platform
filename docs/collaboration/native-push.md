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
