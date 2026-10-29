# Android Attendance capture response-loss acceptance

The owned fixture creates fictional employment and today's 09:00–17:00 remote
roster through the public API. Android explicitly saves one Clock in without
location. A bounded loopback proxy drops the successful response after the real
API commits and blocks later requests until recovery. A separate authenticated
observer checks the real event, original timestamps and independent-review state.
No capture, review, receipt or database time is fabricated.

## Reproduce

Use JDK 21, Docker, Playwright Chromium and the dedicated `hris-mobile-dev` AVD at
`emulator-5560`. Build Platform API/worker JARs and a compatible Mobile dev APK with
`-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/`. Reuse a validated APK when the
application source is unchanged and preserve emulator data.

From Platform `apps/dashboard`:

```sh
node --test integration-native/fixtures/attendance-proxy.test.mjs
npx playwright test --config playwright.native-attendance.config.ts
```

Wait for `Owned Android attendance capture fixture ready`. Private
`.work/attendance-native-ready.json` supplies the fictional account/company/date.
The fixture-only password is in `integration/fixtures/accounts.sql`. Review stale
markers before starting; never reuse a live fixture. Driver time is bounded to
twelve minutes after setup; the entire case has a thirteen-minute limit.

1. Record the dedicated emulator's location-service setting, then disable it using
   `adb -s emulator-5560 shell cmd location set-location-enabled false`. Dismiss any
   system notification without re-enabling location. Forward port 18082, start
   `dev.fajar.hris.mobile.dev/dev.fajar.hris.MainActivity`, sign in and select
   **Attendance Capture Acceptance**. Home shows today's 09:00–17:00 schedule.
2. Select Clock in; grant foreground location permission if requested. The app
   explains that location is disabled and a capture without it goes to review.
   Select **Save without location** once. Home retains the original time with
   **Delivery not confirmed** and Pending attendance. No position is injected.
3. Account → Pending actions shows **Awaiting confirmation: 1** for Attendance.
   Let independent navigation admission settle before measuring Reload summary;
   the reload itself must issue no request.
4. Remove port 18082 forwarding, force-stop/restart the app and reopen the summary.
   The same count and no-request reload survive offline. **Open Attendance** returns
   to Home. Open **Pending attendance**; the original Clock in time/date, unconfirmed
   result and Try again must restore offline.
5. Create private `.work/attendance-native-recover`, restore forwarding and select
   **Try again**. Require **No pending attendance.** Close the dialog: Home retains
   the same time but now shows **Pending review**. Account → Pending actions has
   six empty rows. Sign out and require the sign-in screen.
6. Only then create `.work/attendance-native-done`. The independent observer requires
   one version-zero CLOCK_IN event, offline=true, no location/review/correction,
   original roster snapshot, pending review and zero accepted minutes. Captured
   time lies between readiness and receipt, and receipt precedes final observation.
   Exactly two identical successful native capture commands and no other company
   writes are permitted.

The proxy uses a fixed loopback upstream, bounded headers/request/response sizes,
connection/admission limits, timeouts and owned stream cleanup. Its regression
covers committed response loss, frozen replay, private diagnostics, concurrent
admission and cancellation. Only method/path metadata and operation/input hashes
are recorded. Fixture cleanup owns API/worker/PostgreSQL/proxy; driver cleanup
removes markers/forwarding, restores the original location setting, and stops the
emulator without wiping data.

## Executed result — 29 October 2026

The unchanged Mobile `02743a9f` APK (32,853,156 bytes; SHA-256
`2b603fff2e40e31ee9cf5a45e694ea6aae8c96ae6c72cd4168d920c2e239c0c9`) passed on Android
35 against Platform `b7f42070` artifacts/schema 101. No application rebuild was
required; fixture parent was `ccae6f8f`.

Actual Save without location committed at 04:03 UTC, then lost its response.
One awaiting-confirmation count and the original **11:03 Asia/Jakarta** time
survived offline cold start. After navigation admission settled (four equal samples),
summary reload stayed **21 → 21**, including offline reload. Offline transport used
removed API forwarding; Wi-Fi remained enabled. Explicit retry used the same
operation and body hash, including the original timestamp.

The observer found **one event, version 0**, captured at
`2026-10-29T04:03:23.543Z`, received at `2026-10-29T04:03:24.204424Z`, with **OFFLINE
and UNVERIFIED_CAPTURE**, no location/review, one pending event and **zero accepted
minutes**. Across **30 native requests** there were **two intended company writes**.
Local queue and six summary rows cleared; Home still showed Pending review and
logout passed. Delivery confirmation did not accept evidence for payroll.

The fixture passed in **4.6m / 5.2m including setup**. Proxy regression, strict
TypeScript, Biome, production build and dashboard architecture (four regressions)
passed. Native offline-command and confirmed-Home screenshots were inspected.
An OS location-disabled dialog initially covered login; dismissing it and retrying
configuration after fixture readiness resolved driver setup. No app behavior changed.
All owned containers/services/proxy, markers and forwarding were removed; location
was restored to enabled and the emulator stopped with data retained.
[Tracked measurements](measurements/native-attendance-2026-10-29.json) are sanitized;
local logs/screenshots supplement them. No Mobile code/test rerun or Starter port
applies to this fixture. Physical GPS, external providers and other device outcomes
remain separately unaccepted.
