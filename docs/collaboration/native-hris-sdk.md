# Android SDK through the HRIS gateway

The pinned Android Matrix SDK now exchanges encrypted DIRECT messages in both
directions through the actual HRIS HTTP gateway and an isolated Synapse. Two
fictional employees use separate encrypted SDK stores. Their public device keys,
recipient key query/claim, encrypted to-device envelopes, room sends and classic
sync all pass through current native HRIS admission. The server reads both
resulting events through the same gateway and verifies ciphertext, sender and
absence of a plaintext body. The client verifies the expected body and encrypted
`org.hris.message_id` after decryption. See [evidence](../measurements/collaboration-native-hris-sdk-2026-10-31.json).

The HTTP fixture uses real registration and bootstrap. Device activation and
room-binding publication are explicit SQL fixtures; provider room creation is
real. This test does not replace the separately tested provisioning worker, and
does not accept product outbox persistence, multi-device verification, notifications,
Apple interoperability, calls or meetings. No production service or user phone
is involved. No room/device cleanup calls Synapse logout.

## Sync compatibility

SDK 26.10.23 pins [Rust SDK 8de215a0](https://github.com/matrix-org/matrix-rust-sdk/blob/8de215a0aa0026afa5754ae5f05a6d168d1a1d60/crates/matrix-sdk/src/client/mod.rs),
which always sets `use_state_after=true`. Its pinned
[Ruma caf1ac05 serializer](https://github.com/ruma/ruma/blob/caf1ac05a9fd347158b60505ef8546a2ff18ae7c/crates/ruma-client-api/src/sync/sync_events/v3.rs)
sends that **unprefixed** query key. The gateway accepts exactly one boolean
preference while retaining its advertised Matrix v1.12 `state` response. It does
not advertise state-after support or fabricate `state_after` from an earlier
state base. The actual SDK correctly consumes the standard response. Invalid or
duplicate preferences, arbitrary filters and other unknown queries remain closed.

Earlier attempts timed out after successful key/message sends because the sync
loop received 400. Initial code-only tracing identified an unknown query; the
first attempted MSC-prefixed spelling also failed. The exact serializer resolved
the mismatch. The measurement retains these failures and successful correction.
One concurrent key upload may receive the existing `collaboration_provider_busy`;
the actual SDK handles the transient outcome and completes the exchange. This is
not a claim that every concurrent request succeeds or that the app owns recovery
from a terminated sync loop yet.

## Reproduction and resource ownership

Build `hris-mobile`'s separate `:apps:collaboration-proof:assembleDebug` artifact.
Boot only the existing `hris-mobile-dev` AVD at `emulator-5560`. With JDK 21,
Docker access and `ANDROID_HOME`, run from Platform:

```sh
HRIS_COLLAB_NATIVE_APK=/absolute/path/collaboration-proof-debug.apk \
  ./gradlew --no-parallel --max-workers=1 :apps:server:cleanTest \
  :apps:server:test --tests '*NativeCollaborationSdkInteropTest'
```

Without the opt-in APK variable the class is skipped. Do not run heavy Mobile
builds alongside this provider/emulator test on the shared VPS. Source changes
to the Python driver require `cleanTest`, because they are not Gradle test inputs.

The driver verifies the exact dedicated AVD and debug package, refuses existing
unowned proof data/mappings, and locks emulator ownership. It passes fictional
credentials through app-private stdin, not shell arguments. IPC files are private,
atomic and never overwrite old evidence. The Java fixture always removes its
credential file; the driver uninstalls only its proof package and removes only its
reverse mapping, including cancellation and failed startup. If a child does not
terminate after bounded terminate/kill waits, cleanup still attempts the package
and mapping and reports failure. Normal HRIS app data is retained.

Only bounded code categories, static endpoint shapes, status, numeric timeout,
safe error identifiers and validated fictional receipts enter retained evidence.
The test-only trace caps observations at 512; bodies, credentials, cursor/filter
values and raw resource identifiers are excluded. Trace counters are diagnostic,
not a gateway allowlist. Tests exercise invalid endpoints/evidence, private IPC,
partial results, cancellation, failed installation/mapping and cleanup failures.

Continue product session/store ownership, durable SDK queue restart and encrypted
logical-ID reconciliation, then UI and all remaining P0–P8 work.
