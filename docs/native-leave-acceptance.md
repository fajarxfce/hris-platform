# Android Leave submission response-loss acceptance

The owned fixture creates fictional employment, an overnight Monday shift, two
days of opening balance and one independently assigned approval stage through the
public API. The actual Android client reviews and submits a half-day request.
A bounded loopback proxy loses the successful response after the real API commits;
subsequent transport is blocked until explicit recovery. A separate authenticated
observer verifies the request, policy snapshot, approval and balance. No business
row or timestamp is rewritten to manufacture an outcome.

## Reproduce

Use JDK 21, Docker, Playwright Chromium and the dedicated `hris-mobile-dev` Android
AVD at `emulator-5560`. Build Platform API/worker JARs and a compatible Mobile dev
APK with `-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/`. Reuse a previously
validated APK when its application sources are unchanged; preserve emulator data.

From Platform `apps/dashboard`:

```sh
node --test integration-native/fixtures/leave-proxy.test.mjs
npx playwright test --config playwright.native-leave.config.ts
```

Wait for `Owned Android leave submission fixture ready`. The private
`.work/leave-native-ready.json` supplies the account/company, next Monday and
exact reason. The public fixture-only password is in
`integration/fixtures/accounts.sql`. Review stale markers before starting; do not
reuse an active fixture. The driver has twelve minutes after setup and the full
Playwright case has a thirteen-minute limit.

1. Forward port 18082. Start
   `dev.fajar.hris.mobile.dev/dev.fajar.hris.MainActivity`, sign in and select
   **Leave Command Acceptance**. Open Leave; the opening balance is 2 available,
   zero reserved/used, without requests.
2. Open **Request leave**. Set Start date to the supplied Monday; End date follows
   when necessary. Select **Browser annual leave** after changing dates, choose
   **First half**, and enter the exact reason. Review calculation once: deduction
   0.5 days and remaining 1.5. Submit once.
3. Require the unconfirmed original type/date/half-day/reason and Retry delivery.
   Back to Account → Pending actions. Leave shows **Awaiting confirmation: 1** with
   no matching draft. Let independent navigation/session admission settle, then
   measure Reload summary: it must issue no HTTP request.
4. Remove port 18082 forwarding, force-stop/restart the application, and reopen
   Pending actions. Require the same count offline. Reload leaves the proxy count
   unchanged. Open Leave → Request leave; the original frozen input restores.
5. Create private `.work/leave-native-recover`, restore forwarding and use **Retry
   delivery**. The app opens the confirmed request showing Pending approval, 0.5
   days, the original reason and one Submitted activity. Back twice: all six
   summary rows are empty. Sign out and require the sign-in screen.
6. Only then create `.work/leave-native-done`. The observer requires one version-zero
   request, one original history entry, one independent pending approval stage,
   the retained policy/day snapshot (450 planned/225 charged minutes), no attachment
   or cancellation, exactly 0.5 reserved and 1.5 available, zero consumed and one
   balance-version increment. Native POSTs must be exactly one read-only preview
   followed by two identical successful submission commands.

The proxy fixes its upstream to loopback and bounds connections, admission,
headers, request/response sizes and timers. It records method/path metadata and
operation/body hashes only, never credentials or request bodies. Its regression
covers response loss, frozen replay, privacy, concurrent admission and stream
cleanup. The fixture owns API/worker/PostgreSQL/proxy resources; the driver cleans
markers, forwarding and its emulator without wiping data.

## Executed result — 29 October 2026

Mobile application `02743a9f`, unchanged loopback APK 32,853,156 bytes / SHA-256
`2b603fff2e40e31ee9cf5a45e694ea6aae8c96ae6c72cd4168d920c2e239c0c9`, passed on Android
35 against Platform `b7f42070` artifacts/schema 101. No application rebuild was
needed; fixture parent was `97e33310`.

The final fresh run passed in **2.8m / 3.2m including setup**. Its native summary
retained one unconfirmed command with no duplicate draft across offline cold start.
After independent navigation admission settled, Reload summary stayed **33 → 33**,
including offline reload. The initial counter attempt overlapped two late account/
policy reads (31 → 33); no stable-count claim is made for that interval. Offline
transport used removed API forwarding while Wi-Fi remained enabled.

Retry preserved both operation and payload. The observer found **one request,
version 0, one history entry and one approval stage**, with **0.5 reserved, 1.5
available and zero consumed days**. Balance version increased once. Across **43
native requests**, there were **two intended company writes and one read-only
preview POST**. All six summary rows cleared and logout passed. The server request
still awaited its independent approver; delivery confirmation does not approve it.

The initial run passed its request/balance/native assertions but failed the
fixture's overly broad POST-as-write assertion. Source review confirmed preview
only reads/locks; the fixture now requires its exact preview/submission sequence.
It passed from a new isolated database. An initial UI dump during cold startup
and a tap after automatic navigation required driver re-observation only.

The proxy regression, strict TypeScript, Biome, production build and dashboard
architecture checks (four regressions) passed. Native screenshots were inspected.
All owned containers/services/proxy, markers and forwarding were cleaned; emulator
stopped with data retained. [Tracked measurements](measurements/native-leave-2026-10-29.json)
are sanitized; `.work` logs and screenshots are supplementary. No Mobile code or
Starter port is required, and previous Mobile test suites were not rerun. Other
feature outcomes and provider/physical/iOS acceptance retain their own scope.
