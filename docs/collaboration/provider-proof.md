# Isolated Matrix and LiveKit provider proof

This exercises actual services and browser SDKs. It is one P0 proof, not delivery
of HRIS chat/calling, native background calls, MatrixRTC interoperability or
1,000-participant capacity. Follow [the full delivery ledger](delivery.json).

## Native Android interoperability

The Mobile repository supplies a separate `:apps:collaboration-proof` debug-only
APK, pinned to Matrix Rust Kotlin SDK **26.10.23**. On the dedicated Android 35
AVD, the actual Rust SDK decrypted a browser message and the browser decrypted
its reply. Both corresponding Synapse events contain `m.room.encrypted`, without
either plaintext marker. The combined Android/browser/media run completed in
24.590 seconds; [evidence](../measurements/collaboration-android-web-provider-2026-10-31.json)
pins the APK and driver sources. Twelve driver tests protect target selection,
preexisting data, fixture permissions and cleanup. This does not establish native
offline queue recovery, device verification UX, native media or HRIS admission.

Build the fixture from Mobile with JDK 21 and `ANDROID_HOME` configured:

```sh
./gradlew :apps:collaboration-proof:assembleDebug --no-daemon
```

Boot Mobile's existing `hris-mobile-dev` AVD on port 5560, then from Platform:

```sh
python3 tool/check_collaboration_providers.py --docker-sudo \
  --android-serial emulator-5560 \
  --android-apk ../hris-mobile/apps/collaboration-proof/build/outputs/apk/debug/collaboration-proof-debug.apk
```

`--android-sdk` defaults to `~/Android/Sdk`; build-tools 36.0.0 is used to verify
the artifact's application ID and debuggable flag before installation. The driver
rejects other serials/AVDs and any existing protocol package or conflicting reverse
port. It holds an exclusive emulator lock. Credentials enter the fixture sandbox
through stdin; they never enter ADB arguments. Cleanup stops/removes only this
run's APK and reverse port. No real HRIS package or phone is touched. The fixture's
local store passphrase is temporary lab material, not production key management.

Without Android arguments, the native test is not registered and the sanitized
result does not claim the native check. Native CI belongs to Mobile and pins
this Platform driver. Browser-only CI passed at `9452af1e` in
[41180880132](https://github.com/fajarxfce/hris-platform/actions/runs/41180880132).

## Browser-only run

Requires Node 24+, Docker, Python 3 and Playwright Chromium. Install the exact
dependencies with `npm ci --ignore-scripts` in `tool/collaboration-proof`, followed
by `npx playwright install chromium` where the browser is not already installed.

```sh
python3 -m unittest discover -s tool -p test_collaboration_providers.py
python3 tool/check_collaboration_providers.py
```

This workspace permits `sudo -n docker` but not switching to the Docker group.
Here, use `python3 tool/check_collaboration_providers.py --docker-sudo` to apply
existing sudo authority to Docker commands only. npm and workspace writes stay
under the ordinary user. No Docker socket permission changes are needed.

The driver creates a unique labelled bridge, private temporary database and
fictional Synapse users, with random credentials. Only HTTP/signaling ports are
published, on loopback. Database/media sockets remain on the dedicated bridge.
The bridge is not an egress firewall; Synapse federation, trusted key servers,
public registration, previews and statistics reporting are disabled. Docker's
internal-only bridge suppressed published loopback ports in the tested runtime,
so the fixture uses a separate ordinary bridge with loopback publication.

All private configuration, service logs and browser diagnostics stay in a mode
700 `.work/collaboration/<run>` directory. The driver never reads actual HRIS or
Firebase credentials. `finally` validates owner labels before removing its
containers/network. Image caches and private diagnostic files remain. The six
driver tests cover ownership mismatch, missing labels, log/removal failures,
private file permissions and credential-safe command errors.

`result.json` is sanitized: source hashes, image digests, named check outcomes,
browser counts, duration and cleanup. The dedicated CI workflow uploads only
these summaries, never fixture credentials, browser traces or provider logs.

## Assertions and limits

The browser proof verifies peer signing fingerprints out of band, creates an
encrypted invite-only room, sends a message and reads it at the recipient. The
server event has encrypted type/content and no plaintext marker. The recipient
then stops while another message arrives and recovers after browser reload using
its retained crypto store.

A client generates a random media key and sends it through an encrypted Matrix
test event. The receiving browser decrypts it and connects to LiveKit E2EE. A
synthetic canvas and tone prove actual decoded video dimensions and audio energy.
A third connected client with an unrelated key receives real RTP bytes but cannot
decode frames/audio. This test event is intentionally a protocol experiment;
production MatrixRTC recipient/epoch/rotation behavior remains a separate gate.

The fixture attaches remote audio for Chromium playback before analysing it;
constructing a Web Audio source alone did not cause the tested browser to render
the remote stream. Server signaling success alone was never accepted as media
proof. No microphone/camera from the developer or user is captured.

Synapse rejects the wrong JWT issuer/audience and expiration beyond its 120-second
clock tolerance. It accepts a token expired ten seconds earlier, matching
`synapse/handlers/jwt.py`'s `claims.validate(leeway=120)` in v1.164.1. HRIS must
enforce its own strict grant expiry before the internal exchange and keep the
provider login inaccessible as a bypass. This observation is retained in results.

Pending P0 work includes Apple provider interoperability,
production HRIS admission, durable product queues, device verification UX,
MatrixRTC key distribution, native incoming calls, and dedicated capacity tests.

## Native token renewal

The refresh extension gives only the fictional native session a four-second
access lifetime and requests the standard Matrix refresh token. The native SDK
waits beyond that lifetime, automatically refreshes, persists the new credential
pair through its session delegate and sends an encrypted reply. The public device
signing key must remain unchanged. Both real provider scenarios pass in 30.586s
with cleanup; [evidence](../measurements/collaboration-native-refresh-2026-10-31.json)
binds driver, native sources and APK. Browser sessions retain their ordinary
nonrefreshable lifetime.

Matrix Rust bindings 26.10.23 enable `handle_refresh_tokens` in their builder.
Their pinned Rust source is `8de215a0aa0026afa5754ae5f05a6d168d1a1d60`. The
standard Matrix refresh endpoint updates the live client and invokes the save
delegate. Calling `restore_session` again on an authenticated client instead
violates the SDK's one-time authentication initialization. Product integration
will expose standard opaque gateway credentials; refresh must reauthorize the
current HRIS parent without renewing that HRIS parent or recreating E2EE keys.
The fixture file stores fictional tokens only and is not product key management.
