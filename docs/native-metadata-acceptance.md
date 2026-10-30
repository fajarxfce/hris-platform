# Android document metadata recovery acceptance

The actual Android application retains an incomplete document correction through
an offline process restart, then recovers the exact original command after the API
commits but its response is lost. An independent administrator changes the metadata
before retry. Confirmation must not overwrite that newer header or duplicate history.

## Reproduce

Use JDK 21, Docker, Playwright Chromium and the dedicated `hris-mobile-dev` AVD at
`emulator-5560`. Reuse accepted Platform API/worker JARs when their sources match.
Install a compatible Mobile dev APK built with
`-PapiBaseUrl.dev=http://127.0.0.1:18082/api/v1/`; preserve emulator data. This APK
is only for the owned loopback fixture, never for phone distribution.

In Platform `apps/dashboard`:

```sh
node --test integration-native/fixtures/metadata-proxy.test.mjs
npx playwright test --config playwright.native-metadata.config.ts
```

Review any previous `.work/metadata-native-{ready.json,later.json,recover,done}`
before starting. The fixture creates its own PostgreSQL, API, worker, private
Garage and proxy. It uploads fictional PDF bytes and initial passport metadata
through the actual API, then seeds accepted inspection facts only in the labelled
fixture database, with ordinary constraints/triggers enabled. This establishes
metadata behavior with a published binary; it does not establish antivirus scanning.

Wait for `Owned Android metadata fixture ready`. Private readiness JSON supplies
the fictional company and resource IDs. Sign in as
`browser-native-file-employee@example.invalid` with the fixture password from
`integration/fixtures/accounts.sql`; select **Document Metadata Acceptance**.
The whole manual case and backend lifetime are bounded to thirty minutes: the
first correction must arrive within twelve minutes and completion within fourteen
minutes after the later administrative correction.

1. Forward port 18082. Open Home → All services → Documents → Document details →
   Correct document details. Change category to Work permit, number to
   `NATIVE-WORK-001`, validity to incomplete `203`, and reason to
   **Correct printed identity**. Keep AU and issue date 2010-01-01.
2. Return to Account → Pending actions. Require one draft and zero forwarded
   company mutations. Record HTTP count before/after Reload summary: equal.
3. Disable emulator Wi-Fi/data, remove only port 18082 forwarding, force-stop and
   restart the application. Open Pending actions → Document corrections →
   Historical passport. Require the same incomplete draft and original fields.
4. Restore connectivity/forwarding and explicitly Refresh. Complete validity as
   `2019-01-01`, then Save correction and review/confirm its exact input. The proxy
   consumes the successful API response and drops it. Require one unconfirmed
   command, no duplicate draft, and unchanged HTTP count on summary reload.
5. The independent administrator writes Residence permit / `ADMIN-LATER-001` /
   `2035-01-01`. Wait for `metadata-native-later.json`: document version 4,
   metadata revision 2. Repeat the offline process restart. The retained original
   correction must remain read-only, with Work permit / `NATIVE-WORK-001` / 2019.
6. Create private `metadata-native-recover`, restore connectivity/forwarding,
   Refresh and explicitly Retry correction once. Require **Correction saved**
   with the original input. Done clears its matching draft and command. The
   retained overview is empty and all seven pending groups show no pending actions.
7. Reopen Document details. The header must still contain the later admin value;
   history must show the initial Passport, original Work permit and later Residence
   permit. Sign out and require the sign-in screen before creating private
   `metadata-native-done`.

The independent observer requires exactly two successful forwarded metadata PUTs
with identical operation/body hashes, no other company writes, unchanged binary
revision, document version 4 and exactly metadata revisions 0/1/2. The proxy retains
bounded sanitized paths and hashes, never credentials or command bodies. Its
connection, header/body/response, mutation, event and timeout bounds and stream
cleanup have a real HTTP regression. Lower-level blocked transport retries remain
visible; they are not an automatic application replay.

After successful completion, verify owned ports and containers are gone. Remove
only this fixture's control files, forwarding and device XML; restore networking,
stop the owned emulator and retain its app data. Do not touch the user's phone.

## Executed result — 30 October 2026

Mobile application `3c6db4eb` passed against schema 103 in **432.451 seconds**
including fixture setup/cleanup (6.7 minutes for the manual scenario). The loopback
APK is 34,211,727 bytes, SHA-256
`b2ca67b4d5fb0f354758bafa59ed50018247bc0e99f613c172abca7048904db9`.
Draft summary reload stayed **36 → 36** and uncertain-command reload **48 → 48**.
Both offline process restarts, exact replay, Done cleanup, all three visible
history revisions and normal sign-out passed. The API observer recorded **78
requests, two intended company writes, three metadata revisions**, unchanged
binary revision and the later version-4 header. Five key screenshots were inspected.

Strict TypeScript, Biome, dashboard architecture, production dashboard build and
the proxy regression passed. Six invalid fixture-lifetime inputs were rejected
before resource creation. All owned ports, containers, private connection files,
control markers and forwarding were cleaned; the emulator stopped without a wipe.
[Measurements](measurements/native-metadata-2026-10-30.json) retain source/artifact
hashes, actual outcomes and limits.

The first attempt was incomplete: the backend's ordinary ten-minute lifetime
expired while the manual scenario allowed thirty minutes. Its abandoned driver
was interrupted at 971.758 seconds, exit 130. The metadata configuration now asks
for a bounded thirty-minute lifetime; ordinary and FCM defaults remain unchanged.
That attempt is not relabelled a pass. User runtime/phone/provider state did not
change. Hosted Mobile verification/Apple acceptance remains separate.
