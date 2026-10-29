# Owned Android command acceptance

The separate [document transfer case](native-download-acceptance.md) covers partial
downloads, offline restart, Pending actions, retained offsets and native saved bytes.

The native fixture uses the real API/PostgreSQL/worker and an independently
authenticated server observer. It provisions fictional people, grants, a work
calendar and one published Inbox message. A loopback proxy drops the first
successful `read` command response after the API commits, then blocks native
requests until the driver explicitly restores transport. It records only bounded
method/path metadata and hashes of the Inbox operation/input, never credentials
or raw request bodies. Normal streamed reads retain backpressure; connections,
headers, command bytes and fixture lifetime are bounded.

## Reproduce

Use JDK 21, Docker and Playwright Chromium. Build both Platform JARs and the Mobile
dev APK with `-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/`. Use the dedicated
`hris-mobile-dev` AVD at `emulator-5560`, preserving data. Install the validated APK
on that emulator only, add `adb -s emulator-5560 reverse tcp:18082 tcp:18082` and
start `dev.fajar.hris.mobile.dev/dev.fajar.hris.MainActivity`.

From Platform `apps/dashboard`, run:

```sh
npx playwright test --config playwright.native-integration.config.ts
```

Wait for `Owned Android pending-command fixture ready`. The private
`.work/inbox-native-ready.json` identifies the fictional account/company; the
fixture password is the same public test-only value as `integration/fixtures/accounts.sql`.
The driver has eight minutes after setup; the overall case has a nine-minute bound.
Inspect existing `inbox-native-ready.json`, `inbox-native-recover` and
`inbox-native-done` markers before starting another run. Never reuse an active fixture.

1. Sign in, select **Pending Command Acceptance**, open Inbox and **Pending command
   acceptance**, then choose **Mark as read**. Wait for the retained unconfirmed
   state; `.work/inbox-native-http.json` must record one dropped successful write.
2. Return to Account → Pending actions. Inbox must show **Awaiting confirmation: 1**.
   Compare the request counter before opening/reloading that summary: it must not
   change. The summary does not deliver the command.
3. Remove only port 18082 forwarding and force-stop the app. Start it again offline.
   Account → Pending actions must retain the count. **Open Inbox** and the message
   must retain the original unconfirmed action and Retry, without requiring a new
   server acquisition.
4. Create the private `.work/inbox-native-recover` marker, restore forwarding and
   choose Retry on that retained message. The read action disappears, acknowledgment
   remains available, and Back through the nested Inbox returns to the summary.
   All six rows must now show no pending actions. Sign out.
5. Create `.work/inbox-native-done` only after those native assertions. The fixture
   independently requires exactly two forwarded read commands with identical
   operation/input hashes, server version 1, a non-null read time, no acknowledgment
   and no other native company mutation. Its safe server summary is written only
   after those checks pass.

The fixture stops its proxy/API/worker/database and removes private connection
metadata. The native driver owns removing its two completion markers, forwarding
and stopping the emulator without wiping data. Local screenshots, JSON evidence
and diagnostic traces remain ignored. This manual native case is separate from
the unattended browser suites and does not use the user's phone.

## Executed result — 29 October 2026

Mobile `9dbaf730` built the dev APK in 45 seconds with the loopback override. Its
existing application implementation passed the workflow against Platform `3770b3ff`
application artifacts. The Android 35 UI retained **Awaiting confirmation: 1**
through offline cold start; the message retained Retry. Summary open/reload made
no HTTP request (30 → 30). After reconnection and exact-operation recovery, all
six rows were empty and sign-out returned to login.

The independent fixture passed in 6.0 minutes (6.4 including setup). Across 42
native requests, exactly two company writes used the same Inbox operation/input;
the actual server remained at version 1 with acknowledgment absent. All owned
services, private connection metadata and forwarding were removed; the emulator
was stopped with its data intact. Screenshots were inspected. The emulator's
initial System UI warning was dismissed; the driver also corrected an activity
component and nested navigation target before completing the workflow.

Evidence: `.work/inbox-native-fixture.log`, `.work/inbox-native-server-result.json`,
`.work/inbox-native-http.json`; Mobile retains `inbox-command-native-result.json`
and pending/offline/recovered screenshots under `.work`. TypeScript, Biome and
the browser production build passed. No application source or shared foundation
changed, so this acceptance requires no Starter port. It covers this Inbox
unconfirmed-command path; document-transfer and other feature outcomes retain
their own validation contracts.
