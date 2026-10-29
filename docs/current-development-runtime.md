# Current private development runtime

Started 29 October 2026 with the user's authorization to continue after installing
the Android dev APK. This is a private development environment, not a production
release or proof that the remaining PRD acceptance has passed.

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
over WireGuard with HTTP 200, without ADB forwarding or firewall changes. A raw
netcat dashboard request has not yet established phone-browser acceptance.

Dashboard TypeScript/Vite build passed. The checked
[user service](../deploy/systemd/hris-dashboard-dev.service) is installed, enabled
and running with user lingering enabled. Unit verification and an explicit restart
passed; the host dashboard became ready again. Reboot behavior has not been tested.
Compose services retain `restart: unless-stopped`; the migration is a one-shot job.

The deployment reviewer now accepts explicit RFC 1918 API bindings in development
mode, retaining production loopback and private data-service restrictions. All eight
reviewer tests passed, and the rendered complete dev configuration passed its
sanitized minimum-wiring review. That review is separate from provider delivery.

## Google login preparation

Firebase Authentication and worker FCM are enabled for `cmp-fajar`. Exact-email
provider lookup was authorized by the user; its private result is not tracked.
The development Android app was confirmed as `dev.fajar.hris.mobile.dev`, then its
current signing SHA-1 was registered successfully:
`23:86:0B:51:E4:30:5A:A7:90:00:90:AE:AB:67:83:D3:9A:12:FF:33`.
Management read-back confirmed registration and refreshed config contains Android
and web OAuth clients. Only this development app's public certificate changed.

The intended Google account had not yet signed into this Firebase project. A
human sign-in from the installed phone is pending. Bind only its exact verified
Firebase issuer/UID through the authenticated HRIS administration flow; matching
email alone must never create an HRIS binding or grant. Google exchange and
physical FCM/location acceptance are not established by configuration readiness.

## Continue safely

Bootstrap operator access is private in `/home/fajar/.config/hris/dev-access.txt`.
Provisioning recovery material and browser sessions, if present, are also private
in that directory. Inspect those deliberately without echoing their contents.
SMTP remains disabled outside a separately owned local invitation capture; no
external email provider has been configured. Initial company/person data must be
fictional. Use public commands with normal authorization, MFA, versions, audit and
idempotency; do not bypass business policy by inserting accounts or grants in SQL.

Continue account/company provisioning and real human Google exchange, then record
each measured result here and in the handoff. Leave this persistent stack running;
disposable acceptance fixture cleanup must target only its own resources.
