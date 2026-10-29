# Android expense save response-loss acceptance

The owned fixture creates fictional employment and a receipt-optional Travel
category through the public API. The actual Android application edits one local
working copy, then saves through a bounded loopback proxy. The proxy loses the
successful response after the real API commits and blocks transport until explicit
recovery. An independently authenticated observer checks the real claim and history.
No business database outcome, timestamp or receipt is fabricated.

## Reproduce

Use JDK 21, Docker, Playwright Chromium and the dedicated `hris-mobile-dev` AVD at
`emulator-5560`. Build both Platform JARs and a compatible Mobile dev APK using
`-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/`. Preserve emulator data and reuse
the validated APK when its application sources have not changed.

In Platform `apps/dashboard`:

```sh
node --test integration-native/fixtures/expense-proxy.test.mjs
npx playwright test --config playwright.native-expense.config.ts
```

Wait for `Owned Android expense save fixture ready`. Private
`.work/expense-native-ready.json` supplies the account/company, category, date and
exact inputs. The fixture-only password is in `integration/fixtures/accounts.sql`.
Review stale ready/recover/done markers before starting; never reuse a live fixture.
The driver has twelve minutes after setup; the whole case is bounded to thirteen.

1. Forward port 18082 and start the installed
   `dev.fajar.hris.mobile.dev/dev.fajar.hris.MainActivity`. Sign in, select
   **Expense Save Acceptance**, and open Expenses → New expense claim.
2. Enter the supplied title/reason. Add one Travel line with the supplied date,
   IDR 25,000.25 and **Local taxi**, leaving optional receipts/cost center empty.
   Save the item locally. Before saving the claim, back to Account → Pending
   actions: Expenses must show **Drafts: 1**, with zero company writes. Reloading
   the summary must not change the HTTP count.
3. Open Expenses → New expense claim. The working copy must restore. Use **Save
   draft** once. Require **Confirmation pending** and one dropped successful PUT
   in `.work/expense-native-http.json`. Back twice to Pending actions: require
   **Awaiting confirmation: 1** without an extra draft. Reload must make no request.
4. Remove port 18082 forwarding, force-stop and restart the app. The offline summary
   retains the same single command; reload leaves the request count unchanged.
   Open Expenses → New expense claim: the title, one line and **Check status**
   restore from the frozen command without acquisition.
5. Create private `.work/expense-native-recover`, restore forwarding and use
   **Check status**. The resulting claim details show Draft, IDR 25,000.25, one
   line and one **Draft saved** activity. Back twice: all six summary rows show
   no pending actions. Sign out and require the sign-in screen.
6. Only then create `.work/expense-native-done`. The independent observer requires
   one claim at version/draft revision zero, the original author/title/reason,
   one exact line, no submissions/payments and one history entry. Two identical
   successful forwarded save commands and no additional company writes are required.

The proxy records bounded method/path metadata and command/body hashes only. Its
fixed loopback upstream, header/body/response limits, connection/admission caps,
timeouts and owned stream cleanup are covered by its regression. No credentials
or raw command bodies are written to diagnostics. The fixture owns API/worker,
PostgreSQL and proxy resources; the native driver removes its markers/forwarding
and stops its emulator without wiping data.

## Executed result — 29 October 2026

The unchanged Mobile `02743a9f` loopback APK (32,853,156 bytes, SHA-256
`2b603fff2e40e31ee9cf5a45e694ea6aae8c96ae6c72cd4168d920c2e239c0c9`) passed against
Platform `b7f42070` application artifacts/schema 101. The JARs rebuilt for the
preceding Overtime case were reused; fixture parent was `578a5031`.

Android 35 showed one edited draft and zero company writes before Save. Summary
reload stayed **30 → 30**. After a committed-response loss it showed one awaiting
confirmation and no duplicate draft. Summary reload and actual offline cold start
kept the count at **43 → 43**. API forwarding was removed during the offline phase;
Wi-Fi itself remained enabled. The original frozen command restored offline.

Explicit Check status replayed the same operation and input. The independent API
observer confirmed **one claim, version/draft revision 0, one line, IDR 25,000.25,
one history entry and zero submissions/payments**. There were **51 native requests
and two intended company writes**. All six pending rows cleared and logout passed.

The fixture passed in **8.3 minutes / 8.8 minutes including setup**. Its proxy
regression, strict TypeScript, Biome, production build and dashboard architecture
checks (including four architecture regressions) passed. Offline-command and
confirmed-detail screenshots were inspected. All owned containers/servers/proxy,
markers and forwarding were removed; emulator stopped with data retained.
[Tracked measurements](measurements/native-expense-2026-10-29.json) are sanitized.
Local `.work/native-expense-fixture.log`, server/HTTP JSON and Mobile screenshots
are supplementary. This changes fixture/docs only; Mobile application tests were
not rerun, and no Starter port applies. Other native outcomes and external-provider
or physical/iOS acceptance remain separate.
