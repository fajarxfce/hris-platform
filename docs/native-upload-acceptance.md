# Android interrupted upload and inspection review

This fixture uses the actual Android application, API, PostgreSQL, worker and
private Garage. The employee imports a fictional 3,145,841-byte PDF through the
native file picker. A loopback proxy consumes the first successful chunk response
then drops it, after the server committed one MiB. Further native requests remain
blocked until the driver restores transport through an owned filesystem marker.

The scanner is deliberately disabled. After the real worker reports
`document_scanner_not_configured`, the independent administrator stops that job
through the normal versioned cancellation API. The native application must acquire
`VALIDATION_FAILED`, retain **Needs review: 1** offline, and explicitly request a
second inspection. That job follows the same controlled stop. This exercises real
processing failure/review and cancellation; antivirus acceptance is recorded in
[the separate full-signature workload](workload.md#document-upload-inspection-and-download).
No database lifecycle state, timestamp, trigger or validation verdict is rewritten.

## Reproduce

Use the [JDK 21/Docker-group environment](agent-handoff.md#commands-and-environment).
Build API/worker JARs after application changes. Use a Mobile dev APK built with
`-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/` and the dedicated
`hris-mobile-dev` AVD, serial `emulator-5560`. Preserve emulator app data. From
Platform `apps/dashboard`:

```sh
node --experimental-strip-types --test --test-concurrency=1 --test-timeout=10000 integration-native/fixtures/*.test.mjs
./node_modules/.bin/playwright test --config playwright.native-upload.config.ts
```

Wait for **Owned Android partial-upload/review fixture ready**. The fixture refuses
existing `upload-native-ready.json`, `-source.pdf`, `-recover`, `-done`, `-partial.json`
and `-review-{1,2}.json` files: review their owner before another run. The private
ready file contains only fictional account/company IDs and source size/hash. The
whole case has a twenty-minute deadline; initial upload must reach its interruption
within five minutes after readiness.

1. Reverse port 18082 to the emulator. Copy `.work/upload-native-source.pdf` to
   `/sdcard/Download/pending-upload.pdf`. Launch the HRIS dev application and sign
   in as `browser-native-file-employee@example.invalid`, using the public test-only
   password from `integration/fixtures/accounts.sql`. Choose **Pending Upload
   Acceptance**. If the app started before readiness, Retry authentication config.
2. Home → All services → Documents → On this device → Add file. Select the PDF,
   retain its title/Personal category and employment **UPLOAD-001**, enter a
   description and choose **Upload document**. Require **Needs attention** and
   **Retry upload**. The independent `-partial.json` must report one revision,
   1,048,576 accepted bytes and version 1.
3. Account → Pending actions must show **File transfers: 1** and five empty rows.
   Record the proxy request counter before opening/reloading the summary and
   require it unchanged afterward. Disable Wi-Fi and mobile data, remove only
   reverse port 18082, force-stop and relaunch. Require the restored company and
   same count. Open Documents must reach the retained upload without online reads.
4. Create the owned `upload-native-recover` marker, restore network and forwarding,
   and choose **Retry upload**. The original chunk operation/body must be replayed;
   remaining parts continue at one MiB. Wait for `upload-native-review-1.json`.
   **Check status** must show **Check failed** and **Retry check**.
5. Pending actions must now show **Needs review: 1**, no transfer and five empty
   rows. Reload must make no HTTP request. Repeat the full offline process restart
   and require the same count and retained failed-check detail. Restore transport
   and explicitly choose **Retry check**. Wait for `upload-native-review-2.json`;
   **Check status** must retain the second failed inspection.
6. Choose **Cancel upload**, enter a reason and confirm. Wait for **Upload
   cancelled**. All six summary rows must be empty while the local file still
   exists. Return through Open Documents, confirm **Remove local file**, require
   **No local files yet.**, and sign out. Create `upload-native-done` only after
   these native assertions pass. Wait for the independent fixture result.

The server requires nine successful native document mutations: one declaration,
five chunk calls (the first two identical), two distinct validation commands and
one cancellation. SQL must find one unpublished cancelled revision and four
committed chunks with matching source offsets, lengths, SHA-256, versions 1–4 and
one physical attempt each. This checks that response recovery creates neither a
new revision nor another storage attempt. Other company mutations fail acceptance.

The proxy owns eight connection slots, sixteen-KiB headers, one-MiB binary/eight-KiB
other request limits, eight-KiB mutation response buffering, finite request/header/
upstream deadlines, 32 mutation admissions and a 512-event ring. It stores only
sanitized paths, counts, offsets and command/content hashes; never headers, tokens
or payloads. Completion requires the history to fit the ring. Closing destroys
active client/upstream streams. Node regression exercises exact dropped-response
replay, concurrent admission, binary/JSON limits and active-response disposal.

The fixture owns proxy/API/worker/PostgreSQL/Garage cleanup and private connection
files. It removes source and readiness files in `finally`. The driver owns the
remaining marker cleanup, emulator forwarding/public source/UI-dump cleanup, and
stopping the emulator while retaining its app data and restoring connectivity.

## Executed result — 29 October 2026

Platform application artifacts at `c282fa60` / schema 100 and Mobile `02743a9f`
passed in **8.7 minutes**, **9.2 including setup**. The existing 32,853,156-byte
loopback APK was reused, SHA-256
`2b603fff2e40e31ee9cf5a45e694ea6aae8c96ae6c72cd4168d920c2e239c0c9`.

Both offline restarts retained the expected one-item count and owning file route.
Transfer summary open/reload kept HTTP at **32 → 32**; failed-check summary reload
kept it at **42 → 42**. Retry reused exactly the first chunk operation/input, and
four parts retained their single physical attempts. The source SHA-256 was
`2fced5a6de049a2b5045b3c2f7c34ff2b89c76d7ce0d486a69c183b9584f760a`.
The server observed **57 native requests and nine intended company mutations**,
two inspections, final cancellation and no publication. All six summary rows
became empty before local deletion; deletion and sign-out then succeeded. Both
offline-summary screenshots were inspected.

Three loopback regression cases, strict TypeScript (now including all native
fixtures), Biome, production build and architecture/format checks passed. No
application implementation or shared DI changed, so no new Mobile build, repeated
full application test run or Starter port was necessary. The app initially needed
config Retry because it launched before readiness. Two coordinate taps during
unsettled dialog/keyboard transitions did not submit; fresh UI reads and explicit
taps completed the actions, with exactly one server cancellation. No app fix was
needed. All owned services, private connection files, markers, forwarding and the
public source were removed; the emulator stopped with app data retained.

Local evidence: Platform `.work/upload-native-{fixture,proxy-tests,types,lint,architecture}.log`
and `upload-native-{http,server-result}.json`; Mobile `.work/upload-native-result.json`,
summary counter records, offline-summary UI trees and offline/cancelled screenshots.
The overall PRD remains in progress; this case does not imply actual provider
message delivery, iOS/physical acceptance or every feature's native command outcome.
