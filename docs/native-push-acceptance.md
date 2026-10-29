# Android Firebase registration recovery

The [Mobile registration flow](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/native-push.md)
has actual Android/Firebase SDK/API/PostgreSQL acceptance for token issuance,
registration, response loss, offline process restart and retirement. The worker's
FCM sender is disabled in this fixture; message delivery and scoped hint behavior
are separate acceptance, requiring the server credential. No Firebase Auth user
or Google account login is created by this case.

## Owned fixture

`apps/dashboard/integration-native/push-registration.spec.ts` starts an independent
company and fictional employee/account binding through the real administrative
API. It uses fixture accounts 98/99 in a newly owned database; do not combine it
with another manual native fixture in the same process/database. The helper's
employee setup uses existing leave-scenario infrastructure, without a submitted
leave request. The application receives its real account/company through normal
native login. The fixture never supplies an SDK token to the app.

`fixtures/push-proxy.ts` forwards only to the fixed loopback API on port 18081 and
listens on 18082. The first successful native registration PUT is consumed from
the API, then its response is dropped. Subsequent calls receive 503 until a private
local recovery marker appears. The proxy stores only method/path summaries and
hashed operation/input equality, never headers, credentials or token contents.
It owns eight connections, 16-KiB headers, 8-KiB request/mutation-response bounds,
20-second request and 15-second upstream deadlines. At most sixteen registration/
disable attempts are admitted; capacity is checked before upstream forwarding.
Diagnostics retain 512 events and the final fixture rejects a larger history,
so its no-company-write assertion never depends on a truncated event ring.
Abort, error and disposal paths destroy owned upstream requests.

The independent SQL observer validates the fixture container label and reads only
registration status/version/platform and token-presence booleans. It never obtains
the encrypted token, hash value or a credential. After the dropped response it
requires exactly one enabled version-zero row. At completion it requires exactly
two matching successful PUTs, one successful disable, no company mutation, and one
disabled version-one row with neither encrypted token nor token hash.

## Run and drive the emulator

Build current API and worker JARs. Use JDK 21, Node 24 and the existing Docker group
as shown in [agent handoff](agent-handoff.md#commands-and-environment), then:

```sh
cd apps/dashboard
npx playwright test --config playwright.native-push.config.ts
```

The manual case has a fifteen-minute total limit, including bounded seven-minute
registration and completion waits. It is deliberately separate from the default
browser/native Inbox command. Keep its process alive while driving the dedicated
Android emulator; select its ADB serial explicitly. The phone is outside this
fixture. Start with no prior `push-native-ready.json`, `push-native-recover` or
`push-native-done` file in Platform `.work`; investigate an owned interrupted run
before removing anything.

In Mobile, use the existing ignored Firebase config for `cmp-fajar`. Build a dev
APK with `-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/` and the local Android SDK,
install it only on the dedicated emulator without clearing data, and reverse
`tcp:18082`. The default development API setting is unchanged. This loopback APK
is not a phone/release artifact.

1. Wait for Platform `.work/push-native-ready.json` before starting the app, or
   explicitly Retry configuration if it opened earlier. Sign in as
   `browser-native-file-employee@example.invalid` with the public fixture password
   `Browser-fixture-password-123!`. Select **Push Registration Acceptance**.
2. Open Account → Settings → Inbox notifications. Enable it and explicitly allow
   OS notifications when prompted. The SDK deletes any retired token and obtains
   its own current token. Wait for the pending status and the observer's
   `.work/push-native-registered.json` (version zero committed before response loss).
3. Disable emulator Wi-Fi/mobile data, remove the API forward, force-stop and
   relaunch the app. Return to Settings. The preference stays enabled and the
   original command stays pending with a reachable Retry; no data wipe is used.
4. Create the private empty Platform `.work/push-native-recover` marker, restore
   forwarding and connectivity, then Retry. Wait for the enabled status with no
   pending action. The replay must retain its original operation and input.
5. Disable Inbox notifications. Wait for **Notifications are turned off**, including
   completion of SDK retirement, without repeating a still-running action. Sign
   out and confirm the login screen. Create Platform `.work/push-native-done`.
6. Require the test to pass and inspect the sanitized server result. The fixture
   removes ready/connection files and services in its cleanup. Remove the owned
   recovery/done markers and forwarding, restore connectivity, then stop the
   emulator while retaining app data.

The original Inbox/download helper is optional; the steps above are independently
reproducible from a fresh checkout. No secret needs to be pasted into a command or
committed for the native driver.

## Observed result — 29 October 2026

Mobile `02743a9f` fixes two defects discovered by this acceptance. Its native
session binding retains expiry in milliseconds, while PostgreSQL returns the
registration's microseconds. The initial actual gap was 855 microseconds; valid
metadata failed after successful receipt replay. The mapper now validates at the
retained precision and clamps usable expiry to the local boundary. A later
millisecond stays invalid. An APK upgrade recovered the retained original command.

A replacement fixture then lacked a previously owned family queued for retirement.
Mobile now treats only its exact absent-session result as retired; other failure
classes, unknown codes and other accounts remain retained/untouched. The generic
device-session repository continues preserving the error for other callers.
An upgrade cleared that retained failure before the new registration sequence.

The full final case passed in **9.7 minutes; 10.3 with setup**. Its 51 native requests
included exactly two identical successful registration PUTs and one disable POST.
The server had one registration, version zero before replay and version one after
disable, with no retained encrypted token/hash and no company mutation. The
preference and frozen command survived a real offline process restart. Enabled,
disabled and sign-out UI outcomes were checked; offline/disabled screenshots were
inspected. The final 32,853,156-byte APK has SHA-256
`2b603fff2e40e31ee9cf5a45e694ea6aae8c96ae6c72cd4168d920c2e239c0c9`.

Mobile passed root checks (1,840 desktop/UI tests), 64 identity-domain and 49
identity-data Chromium cases, and configured Android assembly. Platform passed
both native proxy tests (including competing admission, oversized request abort
and active-stream cleanup), Biome, TypeScript, production build, architecture and
formatting. The two proxy files run serially in CI because they own the same ports;
this records local evidence, not a hosted CI result. Services, connection files,
markers, forwarding and the emulator were cleaned without wiping app data. No
phone was connected. The generic SDK foundation is unchanged; no Starter port is
required for these HRIS contracts.

The initial fixture timed out while the precision defect was being fixed and is
not counted as a full pass. Supplementary logs retain it separately as
`.work/push-native-initial-fixture.log`. Final evidence is
`.work/push-native-fixture.log`, `.work/push-native-server-result.json`,
`.work/push-native-proxy-tests-final.log`, and the Mobile validation/handoff logs.
FCM sending/scoped provider hints, Google ID-token exchange, iOS/APNs and physical
device acceptance remain open. The Firebase service-account path question is
pending; this fixture does not require that credential.
