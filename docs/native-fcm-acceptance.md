# Actual FCM delivery on Android

This opt-in case exercises real publication, the owned worker's Google OAuth/FCM
HTTP v1 sender, Firebase Messaging on Android, canonical Inbox synchronization,
OS presentation and notification navigation. [Registration recovery](native-push-acceptance.md)
is separate: its sender remains disabled and its first committed response is lost.
Neither fixture creates a Firebase Authentication or Google user.

## Credential and ownership

The supplied `cmp-fajar` service account is installed on this VPS at
`/home/fajar/.config/hris/fcm-service-account.json`, outside the repositories.
The directory is mode 700 and the file is mode 600. The original supplied home
file is retained with mode 600. Do not print or commit either credential. Android
`google-services.json` config is separate from the server's signing credential.

`tool/dashboard_fcm_fixture.py` requires explicit worker/FCM opt-in, a project ID
and an absolute private regular file of at most 32 KiB. It checks the service-account
type, project match, Google token endpoint and required fields without logging
them. Default fixtures never read the credential and keep FCM disabled, even if
the surrounding shell enables a production sender. Only the owned worker gets the
accepted FCM configuration; the interactive API remains disabled. CI exercises
the configuration boundary with fictional data and does not send real messages.

The manual case creates a fresh PostgreSQL database, company and fictional
employee/account through the public admin API. It requires exactly one enabled
registration owned by fixture account 99 before publishing. The loopback proxy
retains its connection, request, response, attempt and diagnostic bounds but does
not interrupt registration in this case. It never records provider tokens or
authorization headers. The independent SQL observer checks the owned container
label before reading safe receipt counters and token-presence counts.

## Execution

Build current API/worker JARs and the Mobile dev APK with the explicit local API
override `-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/`. Use the ignored Mobile
Firebase configuration for `cmp-fajar`. Start only the dedicated `hris-mobile-dev`
AVD on `emulator-5560`, install without wiping data and reverse `tcp:18082`.
The fixture verifies this AVD identity; never substitute a phone. This local API
APK must not be distributed as a normally configured application.

From Platform `apps/dashboard`, with JDK 21 and the documented Docker group:

```sh
HRIS_TEST_FCM_PROJECT_ID=cmp-fajar \
HRIS_TEST_FCM_CREDENTIALS_FILE=/home/fajar/.config/hris/fcm-service-account.json \
HRIS_TEST_ADB=/home/fajar/Android/Sdk/platform-tools/adb \
npx playwright test --config playwright.native-fcm.config.ts
```

Keep the process alive while driving Android. The total deadline is fifteen
minutes. Before starting, investigate any old owned control files; do not reuse
markers from an interrupted run. Files below live in Platform `.work`; create
empty control markers with mode 600 only after observing the corresponding UI.

1. Wait for `fcm-native-ready.json`. Sign in normally as
   `browser-native-file-employee@example.invalid` with the public fixture password
   `Browser-fixture-password-123!`. Retry configuration if the app opened before
   the API was ready. Select **FCM Delivery Acceptance**.
2. Open Account → Settings → Inbox notifications, explicitly allow OS notifications
   if prompted and wait for **Notifications are enabled for this device**. Keep
   the app in the foreground; create `fcm-native-foreground-ready` within four minutes.
3. Wait for `fcm-native-foreground-notified.json`. The test publishes a fictional
   announcement, requires actual provider acceptance, then independently requires
   an active Android notification tagged with that exact Inbox ID. Its title/body
   must be generic, without the announcement's private contents.
4. Press Android Home without force-stopping the app. Create
   `fcm-native-background-ready`; wait for `fcm-native-background-notified.json`.
   This is a second real publication and independently observed OS notification.
5. Tap the second notification in Android's shade. Verify the app opens
   **FCM background acceptance** in the fixture company. Do not mark it read or
   acknowledge it. Create `fcm-native-opened` only after this UI observation.
6. Disable Inbox notifications and wait for **Notifications are turned off**.
   Sign out to the login screen, then create `fcm-native-done`.
7. Require the test to pass. Inspect `fcm-native-server-result.json`: both Inbox
   rows remain version zero with absent read/acknowledgement, both dispatches
   accepted one target, there are no native company mutations and the registration
   is disabled with no encrypted token/hash. The wrapper removes its owned services
   and database. Remove owned markers and forwarding; stop the emulator while
   retaining app data and restore any changed device settings.

Provider acceptance alone does not prove device delivery. Normal-priority FCM can
wait for its transport connection to recover; the OS observer allows 350 seconds
for the unchanged 300-second provider TTL plus bounded native processing. This is
a finite acceptance wait, not a product retry or a latency guarantee. The worker's
Google HTTP deadlines, push priority and TTL remain unchanged.

Scope replacement and duplicate/revoked hints retain separate automated coverage;
this case exercises the correct account and selected company with actual FCM.
Google authentication, iOS/APNs and physical devices require their own acceptance.

## Observed result — 29 October 2026

The final case passed in **6.2 minutes; 6.6 including setup**, using Platform
runtime `b7f42070` / schema 101 and Mobile application `678783b3`. Both publications
reached real FCM with one accepted target and no rejection. Android showed both
notifications with generic title/body. Tapping the background notification opened
the expected item in the same company. Both rows remained version zero with no
read or acknowledgement. The 55 native requests made no company mutation.
Disabling notifications and signing out left one disabled registration with no
encrypted token or token hash.

The observed intervals from driver-ready markers to OS notifications were 10.032
seconds foreground and 160.633 seconds background, including publication, polling
and Android scheduling. These are observations on this emulator, not an immediacy
guarantee. The first attempt timed out waiting for manual readiness before sending.
The second reached FCM but its 120-second OS observer expired before Google's
transport reconnected and received the message at 08:14:11 UTC. Both failures are
retained separately; neither is counted as a pass. The final 350-second observer
passed without changing the sender priority, TTL or application implementation.

Five new configuration tests (12 related Python fixture tests total), seven serial
native proxy regressions, TypeScript, Biome, dashboard production build and the
architecture check passed. These are local checks, separate from the earlier full
CI. The current 32,853,122-byte APK has SHA-256
`59494858f2da47b37307b7f911960c0f895929aa92e90bc8831a6f6b53fde715`.
No new Mobile implementation or generic Starter port is required.

All owned services, the database, connection/control files, forwarding and temporary
diagnostic database copies were removed. The emulator was stopped without wiping
app data; connectivity and location were unchanged. The service account remains
private outside Git. No phone, production deployment or release was used.
[Tracked measurements](measurements/native-fcm-2026-10-29.json) preserve the sanitized
results and limits; `.work/fcm-native-fixture*.log`, the server result and Mobile
screenshots are supplementary local diagnostics.
