# Organization push callback admission

The group callback use case accepts only bounded, typed `hrisgp1_` capability batches.
The whole batch is validated before any lookup or write: one to 32 unique keys, a valid
optional Matrix event locator and a single original deadline of at most 30 seconds.
Each key commits independently, so a failed later key may retry earlier retained hints.
The HTTP adapter must supply its existing tighter request budget when integrated.

Anonymous hash lookup identifies the account and retained device. Under root account
scope, guards are acquired in account/native/device/gateway/binding order. The current
account security version, hard native lifetime, bound active group device, canonical
selected Matrix identity, provider metadata, hard gateway lifetime, client minimum,
current group directory eligibility and native registration must all agree. Callback
admission never decrypts a Matrix credential or native push token.

Short native access, gateway access, MFA age and provider credential expiry do not stop
an opaque wake. They still gate ordinary interactive content/reply access. A separate
pure completion-provenance check verifies exact original/target native and Matrix
coordinates, fingerprint, generations, completion times and provisioning outcome for
retained LEGACY devices. The existing interactive completion wrapper keeps its own
current admission/time gate; no fabricated interactive admission is used in background.

Missing or permanently revoked/expired native ownership and exactly revoked group
devices permit key retirement. A foreign/inconsistent projection, inactive registration,
stale client, temporary eligibility loss or disabled group rollout suppresses the wake
without deleting its retained key. Failed acquisition remains a retryable failure, not
an unknown key. Global collaboration/push/provider disablement returns unavailable.

Visible locators require exact immutable published or original DIRECT room ownership.
Only an absent successful published lookup permits original-room fallback. Malformed
or failed reads cannot grant ownership. Peer offboarding does not erase the caller's
owned original history; recipient selection/new-send eligibility is not reused here.

Current target and directory inputs are reread before and after the durable append.
Late completion, cancellation, interruption, eligibility loss or changed owner rolls
back the actual queue write. Retries coalesce in V137. Only a pending binding may advance
to publication; an already published capability's timestamp and identity stay exact.
These queue outcomes are never chat delivered/read receipts.

See [accepted validation evidence](../measurements/collaboration-space-push-admission-2026-11-04.json). This change
adds the typed use case and policy, not the mixed HTTP callback adapter or FCM worker.
Shared DI, Mobile background/reply routing, one-visible-notification handover and actual
SDK original-key decryption remain separate acceptance. Group rollout stays disabled;
live deployment and the normal phone build are unchanged. All 54 PRD groups remain open.
