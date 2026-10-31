# Collaboration architecture decision

Status: accepted design; implementation/proof status is in [delivery](delivery.json).

## Services and sources of truth

HRIS remains the identity, company, permission, resource-context and durable
call/meeting-control authority. Add collaboration domain/data/delivery modules;
keep the existing announcement/inbox module intact. HRIS owns membership
provisioning intents, device bindings, call transitions, seats and artifact ACLs.

Matrix Synapse owns ciphertext timelines and protocol synchronization in its
own PostgreSQL database. Disable public registration/federation and prevent
clients bypassing HRIS session/company admission. A session broker uses Synapse's
supported JWT login on an internal path, with subject/audience/expiry checking;
no HRIS password or Firebase credential is copied into Matrix. Bootstrap never
creates participant private keys. Internal provider tokens must expire within ten
minutes and remain behind the private provider network. A lost/losing issuance
expires without calling logout: actual Synapse logout deletes the entire device,
all its tokens and E2EE keys. Only irreversible HRIS device revocation permits
that deletion; see [transport evidence](../measurements/collaboration-matrix-transport-2026-10-31.json). Revocation admission must not depend solely
on eventual provisioning cleanup.

The [actual provider proof](provider-proof.md) found that Synapse 1.164.1 accepts
JWTs within 120 seconds after expiration as clock tolerance. The HRIS broker
must check its stricter grant deadline itself before internal exchange; provider
JWT acceptance does not establish live HRIS admission.

LiveKit owns WebRTC transport, signaling and active tracks. HRIS issues bounded,
room/device/role-scoped grants after admission. A grant is not a substitute for
the HRIS resource state. Redis supports provider routing/ephemeral coordination;
PostgreSQL plus transactional outbox retains durable work. Cross-service effects
reconcile idempotently after commit, without a distributed-transaction claim.

Use REST/OpenAPI for product controls, Matrix HTTPS sync for chat, LiveKit
WebSocket signaling and WebRTC media. gRPC, Kafka or another identity platform
are not prerequisites for this accepted deployment.

## Clients and lifecycle

Android/iOS integrate Matrix Rust SDK through Kotlin/Swift bindings; web uses
matrix-js-sdk with Rust crypto/WASM. Native message delivery uses the SDK's
durable send queue; web implements equivalent durable transaction-ID semantics.
Avoid competing retry owners. Adapter errors retain cancellation and stable
domain failures. Lifecycle-owned sync stops or changes admission on account
replacement; expiry of ordinary freshness never deletes owned local history.

Use native LiveKit Android/Swift and JavaScript clients, with Opus and negotiated
VP8/H.264, simulcast and adaptive subscriptions. Call lifetime belongs to the
application, not a screen. Bind Telecom/CallKit and audio routing to this owner.
Android uses FCM/CallStyle/RemoteInput/foreground service/MediaProjection/PiP;
Apple uses APNs PushKit/CallKit/AVAudioSession/notification extensions/ReplayKit.
VoIP push reports incoming calls promptly and is not a background job transport.

Add a React/TypeScript/Fluent collaboration web application in this repository;
share usable communications components with the existing administrative web
client. Guests use meeting-only routes. Browser profiles/tab leaders and native
extensions must not concurrently mutate one crypto store without supported
cross-process locking. Keep SDK types out of pure domain and render layers.

## Encryption and files

Matrix SDK crypto handles chat; LiveKit E2EE media keys use MatrixRTC distribution
with client-owned keys, per-session recipients and membership rotation. Pin and
interoperability-test the reference protocol/native bindings before promotion.
Never distribute plaintext media keys alongside server-issued access tokens.

Keep encrypted chat media in separate storage from scanner-validated HRIS
documents. Reimbursement ingestion still follows its normal plaintext validation
pipeline. Client encryption does not imply server malware scanning occurred.
Do not add a server bot as a trusted decrypting member of all private rooms.

Google Drive appDataFolder and CloudKit private database store encrypted backup
chunks/manifests. Recovery keys belong to users. Restoration checks identity,
integrity/schema and current admission/redactions before publishing history.
Cross-platform transfer uses an explicitly verified device-to-device channel.

Normal meetings permit authorized Egress/FFmpeg and Python STT/AI workers.
Private mode denies recording, transcription and AI at both admission and media
key distribution. Use faster-whisper/CTranslate2/VAD and vLLM with Qwen3-8B as the
initial evaluated model. AI artifacts are drafts with timestamp provenance;
they cannot execute HRIS commands. Excalidraw supplies the self-hosted whiteboard;
private mode encrypts collaboration updates and snapshots.

## Public control contracts

New contracts under `/api/v1` cover communication session bootstrap/revoke,
company conversations and resource contexts, call create/accept/reject/end and
history, meeting/occurrence administration, guest/lobby/seat admission, and
authorized recording/transcript/notes/attendance/poll exports. Define explicit
Conversation, ConversationContext, CommunicationDevice, CallSession,
CallParticipant, Meeting, MeetingOccurrence, MeetingRole, MeetingMode and
MeetingArtifact types. Keep account/person/employment/company/device identities
distinct. Mutations retain idempotency/versions and transaction-local RLS.

Add permissions and company feature flags without silently rewriting existing
role snapshots. Tokens cannot grant unadmitted seats, bypass host restrictions,
reactivate ended calls, or make one device's losing acceptance win later.

## Proof, portability and operation

Start with isolated provider experiments on fictional identities; they must not
contact real employees or use production credentials. Provider availability,
cryptographic interoperability, HRIS integration, device acceptance and capacity
are separate evidence categories. Public enablement follows their required gates.

Mobile proves reusable UI/native/ownership changes before Starter receives them.
HRIS identity adapters, resource policies, routes and service configuration stay
downstream. Record matching source/test evidence and each remaining platform gap.

Self-hosting needs license compliance for Synapse (AGPL/commercial), SDKs, media
and model artifacts. Pin artifacts, retain notices and collect SBOMs. Separate
media/AI pools from the HRIS database; bound work and support draining. Restore
drills include databases, private objects and separately protected server keys.

References checked during planning:

- https://github.com/matrix-org/matrix-rust-sdk
- https://github.com/matrix-org/matrix-js-sdk
- https://element-hq.github.io/synapse/latest/jwt.html
- https://docs.livekit.io/transport/encryption/
- https://docs.livekit.io/transport/self-hosting/distributed/
- https://developer.android.com/develop/connectivity/telecom/voip-app/telecom
- https://developer.apple.com/documentation/pushkit/responding-to-voip-notifications-from-pushkit
