# Android partial-download recovery

This manual fixture uses the actual API, PostgreSQL, worker, private Garage and
Android application. A fictional administrator uploads a 3,145,841-byte file
through the API in four storage parts and assigns its employee reader. Normal
constraints/triggers publish a **fictional accepted inspection result**; the
scanner is disabled for this transfer case. Real antivirus acceptance is recorded
separately in [document workloads](workload.md#document-upload-inspection-and-download).

A bounded loopback proxy allows the first authenticated one-MiB range, then returns
503 before the second range reaches the API. Further native requests remain blocked
until the driver restores transport. Only methods, sanitized paths, counts and
range byte/hash metadata are retained. There are eight connection slots, finite
header/request/upstream deadlines, an eight-KiB request-body limit, a one-MiB
content-response bound, sixteen content-request admissions and a 512-entry event
ring. Completion closes client/upstream streams and the server. Private filesystem
markers control the fixture; no network control endpoint exists.

## Reproduce

Use the [JDK 21/Docker-group environment](agent-handoff.md#commands-and-environment).
Build the Platform API/worker JARs if application code changed. Use a Mobile dev APK
built with `-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/`, the dedicated
`hris-mobile-dev` AVD/`emulator-5560`, and preserve emulator data. Do not drive the
user's phone. From Platform `apps/dashboard`:

```sh
node --experimental-strip-types --test --test-timeout=10000 integration-native/fixtures/download-proxy.test.mjs
npx playwright test --config playwright.native-download.config.ts
```

Wait for `Owned Android partial-download fixture ready` before launching the app.
The fixture refuses existing `download-native-ready.json`, `download-native-recover`
or `download-native-done` markers; inspect their owner before removing any. Its
private ready file gives the account/company/source IDs, source size/hash and proxy
port. The fictional password is the public test-only value in `accounts.sql`.
The native driver has fourteen minutes after setup; the whole case is bounded to
fifteen minutes. The existing Inbox config explicitly selects its own spec.

1. Install the APK on the dedicated emulator, reverse port 18082, and launch
   `dev.fajar.hris.mobile.dev/dev.fajar.hris.MainActivity`. If launched before the
   fixture was ready, Retry the failed authentication-configuration acquisition.
2. Sign in as `browser-native-file-employee@example.invalid`, select **Pending
   Transfer Acceptance**, then Home → All services → Documents. Download **Pending
   transfer document**. Require **Download incomplete** and **Resume download**.
   The proxy must show one completed range, `bytes=0-1048575`, with 1,048,576 bytes.
3. Return to Account. Record the proxy counter before opening Pending actions and
   reloading its summary. Require **File transfers: 1**, five other empty rows, and
   an unchanged request count. The summary must perform no transfer delivery.
4. Remove only port 18082 forwarding, force-stop the app and relaunch offline.
   Account → Pending actions must retain the transfer. **Open Documents** must open
   the local-file list; `pending-transfer.pdf` must still open its incomplete
   download route without a new server acquisition.
5. Create the private `download-native-recover` marker, restore forwarding and
   choose **Resume download** on that retained file. Require **Downloaded**.
   Resume must start at byte 1,048,576. Choose **Save a copy**, use the Android
   Downloads destination and require **Copy saved.** Compare the saved file's
   length and SHA-256 against the ready file.
6. Back through the local-file list returns to Pending actions. Require six
   **No pending actions** rows, then sign out. Create `download-native-done` only
   after all native assertions pass.

The independent fixture requires four successful ranges in order, their exact
bytes/hashes, retained resume offset and zero native company mutations. It writes
`download-native-server-result.json` only after those checks. The fixture owns
proxy/API/worker/database/Garage disposal and connection/configuration-file cleanup.
The driver owns completion-marker cleanup, forwarding, the created public copy and
stopping the emulator with its app data retained.

## Executed result — 29 October 2026

The unchanged Mobile application at `c650e0d7` reused the previously built loopback
APK (32,852,906 bytes, SHA-256
`70ea4121020fcd9092c9d587fc2c99faee26c2230b54a6bbfa86706f15c5008f`). Platform application
artifacts were `adfb7b32` / schema 99. The manual fixture passed in **9.7 minutes**
(**10.2 with setup**); this duration includes the native driver.

The one-MiB partial file and **File transfers: 1** survived offline process restart.
Summary open/reload kept the server counter **31 → 31**. Resume sent only the three
remaining ranges, followed by an authorized native save. The saved 3,145,841 bytes
matched SHA-256 `51c7b10f2eb0a8897a8507cca1be050125a585e53f7aa93a75a54ba684931fb9`.
The app showed **Copy saved.**, all six summary rows were empty and sign-out
succeeded. The server observed **49 native requests and zero company mutations**.
Offline-summary and saved-copy screenshots were inspected.

The loopback regression also passed interruption/recovery, competing request
admission at the sixteen-attempt bound and cleanup during an active response.
Post-acceptance review moved that bound to admission so concurrent unfinished
responses cannot over-admit; the regression exercises this case. Biome, strict
TypeScript, production build, architecture and formatting passed. CI runs the
finite proxy regression; manual Android acceptance is not an unattended CI claim.

The early launch required explicit authentication-config Retry; one automation
read occurred before the restarted UI was ready and was retried after startup.
The application implementation required no fix. All owned services, private
connection/configuration files, control markers, forwarding and the created public
copy were removed. The emulator stopped without wiping app data. No Mobile source,
Starter port, live deployment or external-provider/physical-device acceptance is implied.

Local evidence: Platform `.work/download-native-fixture.log`,
`download-native-http.json`, `download-native-server-result.json`,
`download-native-proxy-final.log`, `download-native-architecture.log` and final
lint/type logs. Mobile retains `.work/download-native-result.json`, before/after
counter files, and incomplete/offline/saved/recovered/signed-out screenshots.
