# Current private development runtime

Started 29 October 2026 with the user's authorization to continue after installing
the Android dev APK. This is a private development environment, not a production
release or proof that the remaining PRD acceptance has passed.

## Latest authorized Android delivery

The normal dev APK from Mobile application `d36a1bc9`, built at `0fc16ce4`, was
installed successfully on the connected phone using `--install-only`. It retains
the private WireGuard API; binary inspection excluded the fixture loopback endpoint
and fictional app-link host. The APK is 32,872,797 bytes, SHA-256
`85ef887b4904107482354e9d0ab40b51e2fad7b786c3112f3a24334b078dbde4`.
The first-install time was unchanged and no phone UI was driven. Prior actual
Google → HRIS → Home acceptance below used `678783b3`; an install success is not
a new sign-in observation. [Delivery evidence](measurements/android-update-2026-10-29.json)
retains that distinction. The persistent API/dashboard still returned HTTP 200
after cleaning the separate HTTPS acceptance fixture.

## Ownership and persistence

Compose project `hris-dev` runs from `/home/fajar/hris/hris-platform`. Its ignored
`.env` is a symlink to `/home/fajar/.config/hris/dev.env` (mode 600). It selects
`compose.yml`, `compose.documents.yml`, `compose.wireguard.yml` and
`compose.push.yml`. Keep all overlays on future updates. Separate random database
passwords and identity/sync keyrings are already configured. Preserve these keys
and named volumes; generating a new env file is not credential rotation.

There were no existing containers or named development volumes at inventory.
Sixty retained anonymous volumes were left untouched. The new project owns its
PostgreSQL, Garage metadata/content and ClamAV signature volumes. Never prune or
delete these to update the environment.

The supplied Firebase service account remains in
`/home/fajar/.config/hris/fcm-service-account.json` (mode 600). The worker binds a
separate matching `fcm-container-service-account.json`, owned by UID/GID 10001 with
mode 400, read-only at `/run/hris/fcm-service-account.json`. This lets the non-root
worker read its credential without widening the host's original file permissions.
Both files and all account access material stay outside Git.

## Services and measured checks

| Component | Current address/status |
| --- | --- |
| API | `http://10.77.77.1:8088/api/v1/`; loopback 8088 also retained |
| Dashboard | `http://10.77.77.1:5173`, proxying the loopback API |
| Database | Schema 101; host publication only on `127.0.0.1:55432` |
| Worker | Separate `hris_worker` role, FCM/storage/scanner enabled |
| Garage | Private `hris-dev-documents` bucket initialized |
| ClamAV | Updater and scanner healthy after actual signature download |

Both application JAR tasks passed (125 tasks, 9s, artifacts unchanged); Docker
images built and migration exited successfully. SQL inspection confirms neither
`hris_app` nor `hris_worker` is superuser or has BYPASSRLS. Host API health and
dashboard requests returned HTTP 200. The phone independently reached API health
and dashboard HTML over WireGuard with HTTP 200, without ADB forwarding or firewall
changes. Keep the netcat request input open until the response arrives; immediate
stdin closure produced an empty dashboard response during the first probe. This
is phone network evidence, not phone UI acceptance.

Dashboard TypeScript/Vite build passed. The checked
[user service](../deploy/systemd/hris-dashboard-dev.service) is installed, enabled
and running with user lingering enabled. Unit verification and an explicit restart
passed; the host dashboard became ready again. Reboot behavior has not been tested.
Compose services retain `restart: unless-stopped`; the migration is a one-shot job.

The deployment reviewer now accepts explicit RFC 1918 API bindings in development
mode, retaining production loopback and private data-service restrictions. All eight
reviewer tests passed, and the rendered complete dev configuration passed its
sanitized minimum-wiring review. That review is separate from provider delivery.

## Accepted Google login on the phone

Firebase Authentication and worker FCM are enabled for `cmp-fajar`. Exact-email
provider lookup was authorized by the user; its private result is not tracked.
The development Android app was confirmed as `dev.fajar.hris.mobile.dev`, then its
current signing SHA-1 was registered successfully:
`23:86:0B:51:E4:30:5A:A7:90:00:90:AE:AB:67:83:D3:9A:12:FF:33`.
Management read-back confirmed registration and refreshed config contains Android
and web OAuth clients. Only this development app's public certificate changed.

The user subsequently tried Google on the installed phone and reported HRIS's
unlinked-account rejection. Exact Firebase lookup now confirmed a verified email
and `google.com` provider. Its exact issuer/UID was bound through authenticated
HRIS administration. The user then retried, selected the company and confirmed
**“Sudah masuk Home”**. The authorized account-session API independently returned one
new native session created at `2026-10-29T10:24:50.263372Z`. Google → Firebase → HRIS
→ company/Home acceptance is complete on this installed Android APK. Matching
email alone did not create a binding; no phone UI was driven. See
[sanitized Google evidence](measurements/native-google-2026-10-29.json).

V16 permits one active subject per account/issuer. Adding the real Google UID while
the fictional password UID remained active initially returned 409 `data_conflict`.
The owned fictional binding was reviewed and deactivated through the public API,
then the retained Google command succeeded. Binding history, employee account and
grants were preserved. The previous emulator session was consequently revoked;
its cached data remains on the stopped emulator. Do not reactivate its old Firebase
binding over the real user's identity. No schema or authentication policy changed.

The refreshed ignored Mobile `google-services.json` retains all three HRIS flavors.
Its web client matches the installed APK, so certificate registration required no
new phone build or installation. A separate, explicitly owned fictional Firebase
email/password account supplied the successful Android setup acceptance below;
that binding is now retired in favor of the user's Google UID. It does not stand
in for this separate user-confirmed Google/Home acceptance.

Firebase Management also created the three HRIS iOS apps and returned matching
ignored `apps/ios/Configuration/{dev,staging,prod}/GoogleService-Info.plist` files.
Each has its exact bundle ID, project, OAuth client and reversed client. This
resolves the unavailable public iOS configuration prerequisite. Mobile's iOS
password/bundle and owned Google PKCE adapters passed full CI `40858176601` at
`43cd8ec6`: all four jobs, 97 native cases and the complete Xcode app. Generic
Starter port `e0c6071` followed that proof, passed root/Android/Chromium checks
and all three jobs in full CI `40864341588`, including 77 native cases in 22
downloaded suites and Xcode. Starter's preceding password port passed
62 native cases/Xcode in `40861505322`; its own matching plists are locally ignored.
APNs/device input and actual iOS provider acceptance remain separate. These new
iOS sources do not change the installed Android APK or the accepted binding.

## Usable fictional company and actual acceptance

The bootstrap operator enrolled MFA through the dashboard, with its secret and ten
recovery codes saved privately. Browser login and company Overview passed before
and after the checkpoint. Public commands created `PT Contoh Nusantara` (`HRISDEMO`)
and `Karyawan Demo` (`DEMO-001`). One temporary loopback SMTP capture ran inside the
worker network namespace and accepted only fictional recipients. The real worker
delivered one invitation, which was consumed through the public credential API.
The capture stopped and removed its messages; API/worker are back to mail disabled.
No external email was sent. No business rows or grants were inserted through SQL.

The employee has the standard 12-permission employee role and no platform grants.
An independent administrator linked its account to the person. Native login,
company access and exactly one owned employment passed; global account-directory
access returned 403. The bootstrap operator remains separate from this tester.

The demo includes a Monday–Friday 08:00–17:00 remote shift with a 60-minute break,
two fictional leave days in 2026, a demo expense category and independent operator
approval for Leave, Leave cancellation, Overtime and Expenses. These are examples
for trying the application, not statutory entitlements or production policies.

The same normal dev APK installed on the phone was exercised on the owned Android
35 emulator: Firebase email/password → company selection → Home showed the correct
employee, company and schedule. Documents synchronized `Panduan HRIS Demo`; its
detail then displayed **Downloaded**, 734 B. No phone UI was driven. The emulator
was stopped with app data retained; its current local session belongs to this
persistent demo environment, not the earlier disposable fixture.

That PDF was declared/uploaded through public HRIS commands, inspected by the real
worker using the configured private Garage/ClamAV, and published as `READY`, version
3. Downloaded bytes matched SHA-256
`02e49d8b995d5e7e442604ce5e48d165c0948f3d63d7317711f1654816e22fad`.
The authenticated API/worker integration views also confirmed the current runtime
flags. Retained reports from prior worker processes become stale normally; only the
current process describes current flags. Real FCM acceptance remains recorded in
its earlier dedicated fixture, rather than inferred from these configuration rows.

## Verified initial checkpoint

API, worker and Garage were stopped gracefully in that order. The existing
`tool/recovery_set.py` captured and verified the `hris-dev` sources in isolated
temporary services, then the source services were restarted. Capture plus isolated
verification, including the stops, took 7.668s; this excludes application restart
and is not a production recovery objective.

The private data checkpoint is
`/home/fajar/.local/state/hris-platform/recovery-data/dev-20261029T093500`; the separate
key pack is under `recovery-keys/dev-20261029T093500`. Actual verification confirmed
schema 101, zero failed migrations/invalid constraints, 187 public relations,
restricted API/worker roles, one decrypted identity envelope and one 734-byte S3
object. Isolated verification resources were cleaned. A separate private
`/home/fajar/.config/hris/dev-configuration-checkpoint-2026-10-29` retains matching
configuration and access records. This initial checkpoint precedes replacement of
the fictional Firebase binding by the real Google UID. These are local checkpoints,
not off-host backups; do not treat the earlier identity state as a current cutover.

After restart, host and phone API/dashboard checks returned HTTP 200, browser
operator login/MFA/Overview passed again, the document remained ready with identical
download bytes, and the complete configuration review passed. Measured evidence is
in [the sanitized runtime record](measurements/development-runtime-2026-10-29.json).

## Verified checkpoint after Google acceptance

After the user confirmed Google/company/Home, a new coordinated checkpoint captured
that binding and session state. Data is private at
`/home/fajar/.local/state/hris-platform/recovery-data/dev-google-20261029T103103`,
with the separate key pack under `recovery-keys/dev-google-20261029T103103` and
current configuration/access records in
`/home/fajar/.config/hris/dev-google-configuration-checkpoint-2026-10-29`.
This is the current local checkpoint; the initial checkpoint above remains historical.

Capture and isolated verification took 8.921 seconds including graceful stops;
14.851 seconds includes source restart. Verification passed schema 101, 187 public
relations, zero failed migrations/invalid constraints, restricted runtime roles,
one decrypted identity envelope and one 734-byte S3 object. Temporary recovery
resources were cleaned and persistent source volumes retained. API and the
WireGuard-bound dashboard returned HTTP 200; the same new Google native session
remained valid after restart. A loopback dashboard probe was refused because its
service binds `10.77.77.1`, not loopback; the correct configured address passed.

The preceding attempt `dev-google-20261029T102855` failed because its key verifier
inherited host Java 17 instead of the required Java 21. It created no complete
recovery-set manifest and the source was restarted automatically. Private failure
logs are retained; the successful retry explicitly selected JDK 21. Neither local
checkpoint substitutes for off-host backup or production recovery acceptance.

## Continue safely

Operator access and the active mobile login instructions are private in
`/home/fajar/.config/hris/dev-access.txt`. Its operator authenticator material must
remain available; enrollment is not reset by a restart. The private `dev-*-state.json`
files retain provisioning IDs/receipts, and `dev-admin-browser.json` is sensitive
session material. Inspect deliberately without echoing contents or committing them.

The user's Google email is already known privately and its exact binding is saved.
Do not ask for the email, FCM credential or Home confirmation again: all three
are resolved. The user-confirmed Home has a corresponding new native session;
use the private session-metadata review and exact-email lookup only if a new
problem requires it. The private `bind-dev-firebase.mjs google` retains the successful
operation receipt; it is not a reason to add another binding. Do not invent a UID,
set the real user's password, automatically link by email, or drive the phone UI.

Private provisioning scripts are supplementary continuation aids for this existing
environment, not a general deployment interface. Do not rerun invitation setup
without its owned capture or regenerate existing keyrings. Leave the persistent
stack running; disposable fixture cleanup must target only its own resources.
