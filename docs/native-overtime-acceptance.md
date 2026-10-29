# Android overtime response-loss acceptance

The owned native fixture creates fictional employment, a company holiday and
independent overtime approval policy through the public API. The actual Android
client submits one plan through a bounded loopback proxy. After the API commits,
the proxy drops its successful response and blocks further native requests until
the driver explicitly restores transport. An independently authenticated observer
later checks the real request and history. No database outcome is fabricated.

## Reproduce

Use JDK 21, Docker, Playwright Chromium and the dedicated `hris-mobile-dev` AVD at
`emulator-5560`. Build both Platform JARs and a compatible Mobile dev APK using
`-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/`. Preserve emulator app data; use
the tested APK unchanged when its application source is unchanged.

In Platform `apps/dashboard`:

```sh
node --test integration-native/fixtures/overtime-proxy.test.mjs
npx playwright test --config playwright.native-overtime.config.ts
```

Wait for `Owned Android overtime pending-command fixture ready`. The private
`.work/overtime-native-ready.json` supplies the fictional account/company, work
date and exact input. Its password is the public fixture-only password from
`integration/fixtures/accounts.sql`. Review existing ready/recover/done markers
before another run; an active fixture cannot be reused. The driver has twelve
minutes after setup; the complete Playwright case is bounded to thirteen minutes.

1. Install on the dedicated emulator, forward port 18082, and start
   `dev.fajar.hris.mobile.dev/dev.fajar.hris.MainActivity`. Sign in and select
   **Overtime Command Acceptance**. Open Overtime → Plan overtime. Use the supplied
   work date, 08:00–11:00 company-local time, a 15-minute break and exact reason.
2. Submit once. Require the unconfirmed retained-command view and one dropped
   successful write in `.work/overtime-native-http.json`.
3. Back to Account → Pending actions. Overtime must show **Awaiting confirmation:
   1**, without an extra matching draft. Opening/reloading the summary must not
   change the proxy request count.
4. Remove only port 18082 forwarding, force-stop and restart the app. The same
   summary count must remain offline. Open Overtime → Plan overtime; the original
   date, interval, break-derived duration, reason and Retry must restore without
   server acquisition.
5. Create the private `.work/overtime-native-recover` marker, restore forwarding
   and use **Retry delivery**. The resulting request retains its original plan
   with one **Plan created** history entry. Back twice returns to the summary;
   all six rows must show no pending actions. Sign out.
6. Create `.work/overtime-native-done` after those native assertions. The observer
   requires one plan at version zero, the original 165-minute interval, one history
   entry, no actual-time submission and exactly two identical forwarded plan
   commands. Any additional native company mutation fails the fixture.

The proxy logs bounded method/path metadata and operation/input hashes, never
credentials or command bodies. It fixes its upstream to loopback, caps connection,
header/body/response sizes and admitted mutation attempts, and closes upstream
streams on cancellation/disposal. Its regression verifies committed response
loss, immutable replay, privacy, concurrent admission and stream cleanup.
The fixture owns proxy/API/worker/PostgreSQL resources. The native driver removes
its markers/forwarding and stops the emulator without wiping its data.

## Executed result — 29 October 2026

The unchanged Mobile `02743a9f` loopback APK (32,853,156 bytes, SHA-256
`2b603fff2e40e31ee9cf5a45e694ea6aae8c96ae6c72cd4168d920c2e239c0c9`) passed against
Platform `b7f42070` application artifacts/schema 101. Both JARs rebuilt in 37s.
The first UI dump encountered a transient empty tree during startup; retrying the
driver acquired the login screen. No app behavior changed.

Actual Android 35 restored the unconfirmed plan and summary through offline cold
start. The summary request count stayed **39 → 39**, including reload after
restart. Explicit retry used the same operation and body hashes. The independent
server found **one plan, version 0, one history entry and 165 planned minutes**.
There were **54 native requests and two intended company writes**. All six summary
rows cleared and logout returned to sign-in.

The fixture passed in **7.7 minutes / 8.2 minutes including setup**. The proxy
regression, TypeScript, Biome, production build and dashboard architecture checks
passed. Native screenshots were inspected. Owned containers, proxy/servers,
completion markers and forwarding were removed; emulator data was retained.
[Tracked measurements](measurements/native-overtime-2026-10-29.json) preserve the
sanitized result. `.work/native-overtime-fixture.log`, HTTP/server-result JSON and
Mobile screenshots are supplementary. This acceptance changes fixture/docs only;
there is no Mobile implementation or Starter port. Other native feature outcomes
and external-provider/device acceptance remain separate.
