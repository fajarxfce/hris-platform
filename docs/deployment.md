# Deployment and cutover runbook

Use this runbook to prepare a specific release and execute its approved maintenance
window. Git pushes, local builds and fictional acceptance fixtures do not authorize
changing a live deployment. This repository currently has local workload/recovery
evidence; no live cutover, production RPO/RTO or external-provider readiness is
claimed by this document.

## Release record and prerequisites

Create a private record from [deployment-record.md](deployment-record.md), outside
Git, for each target. Fill in the host, operator, approved window, complete old/new
commit and image identities, HTTPS origin, exact Compose files/project, retained
volumes, checkpoint locations, rollback decision and acceptance owner. Keep secrets
in their existing protected files/secret store, referenced by custody/location only.
Record the specific RPO/RTO required by the operator and the latest measured restore
time; leave unmeasured results explicitly unmeasured.

The existing Compose project name is part of the data identity. Changing it creates
different default volume names; using a second project is not an in-place upgrade.
Preserve the original project and overlays, including any approved VPN bindings.
The deployment must have enough disk for original data, checkpoint archives,
isolated restored copies, image/artifact retention and logs at the same time.
The local verifier's 768-MiB PostgreSQL data filesystem and 16-GiB storage archive
limit are deliberate: a larger deployment needs a separately provisioned, reviewed
restore procedure before its window. Never infer recoverability from a dump file
existing or raise a bound without a successful corresponding restore exercise.

Build candidate API/worker JARs with JDK 21 and the dashboard with Node 24+ from the
same clean, reviewed commit. Use the lockfile and record relevant CI/local checks:

```sh
./gradlew --no-daemon check :apps:server:bootJar :apps:worker:bootJar
npm --prefix apps/dashboard ci
npm --prefix apps/dashboard run check
npm --prefix apps/dashboard run build
```

Keep both preceding application images and the preceding dashboard static bundle.
Build/tag candidate images separately before the window. The Dockerfile defaults
to the server JAR; worker builds must retain `APP_JAR=apps/worker/build/libs/apps-worker.jar`.
Retain immutable image IDs/digests with the approved Compose image override rather
than relying on a mutable project tag. The dashboard artifact is
`apps/dashboard/dist`; the development Vite server is not the production frontend.

## HTTPS, secrets and service wiring

The chosen frontend/reverse proxy must serve that static bundle and proxy `/api/`
to the private API on the same HTTPS origin. Apply the SPA fallback to frontend
routes only: API errors must not become `index.html`. Preserve POST bodies, query
strings, `Authorization`, cookies, CSRF, idempotency, Range and ETag headers. Allow
the application's one-MiB chunks and bounded long downloads; do not retry state-
changing upstream requests automatically. Restrict direct actuator access to the
operator network. Validate the actual proxy's configuration before reloading it.
TLS certificate ownership, renewal, public routing and maintenance routing belong
to the target's release record; this repository does not provision a public origin.

Use `HRIS_SECURE_COOKIES=true` and the canonical HTTPS `HRIS_PUBLIC_URL`.
Keep loopback HTTP/SMTP exceptions off. OIDC callbacks and credential links derive
from that configured origin; forwarded headers cannot substitute for it. Preserve
the mobile URL/package/provider configuration reviewed for this environment.
Do not redirect a production mobile build to an unreviewed HTTP development origin.

Keep separate PostgreSQL migration, API and worker credentials. Changing an env
file does not change passwords in an existing PostgreSQL volume: password rotation
is a separate coordinated database/runtime operation. Runtime roles must retain
`NOSUPERUSER`/`NOBYPASSRLS`; only the migration task receives migration credentials.
Keep current and retired identity keys needed by retained records/checkpoints,
and the independent sync keyring. Do not replace keyrings during an image update.

For documents, include `compose.documents.yml` and preserve Garage metadata/content
and signature volumes. The checked-in ClamAV total scan budget is 250 MiB, with
105-MiB file/stream bounds and the remaining [limits](storage.md). Signature
downloading needs updater egress; private S3/clamd ports need no public binding.
For push, include `compose.push.yml` only with the separately supplied service-
account file, readable by the worker's UID 10001, mounted read-only without automatic
host-path creation. Public Android Firebase configuration cannot replace this file.
Firebase Authentication, generic OIDC, SMTP and FCM have separate configuration
and acceptance contracts: [Firebase](firebase-auth.md), [SSO](sso.md),
[credential email](mail.md), [native push](push.md).

Set up shell variables for the reviewed target; use its actual paths and complete
overlay list. These are examples, not commands executed against a target by this
documentation change:

```sh
hris_release=/srv/hris/releases/APPROVED_COMMIT
hris_environment=/srv/hris/config/runtime.env
hris_project=hris-platform
hris_compose=(docker compose --project-name "$hris_project"
  --env-file "$hris_environment"
  -f "$hris_release/compose.yml"
  -f "$hris_release/compose.documents.yml"
  -f /srv/hris/config/approved-images.yml)
```

Retain `compose.push.yml`/VPN/target overrides when applicable. Do not add a
WireGuard interface binding to a different host without reviewing its actual
address. Check the rendered configuration without writing it to a report:

```sh
set -o pipefail
"${hris_compose[@]}" config --format json |
  python3 "$hris_release/tool/review_deployment.py" --production
```

The bounded review emits only fixed API/worker integration flags and safe issue
categories. It checks minimum required fields, scanner/storage dependencies,
shared API/worker flag agreement, the FCM read-only bind and private application/
data-service port bindings. Production mode additionally checks secure cookies,
the HTTPS origin shape and disabled development transport exceptions. Worker FCM
may correctly be enabled while API FCM is disabled. The six API rows do not describe
the worker; Firebase/OIDC request handling is not a worker capability.

`minimumWiringPassed` checks this explicit configuration subset. It does not validate
secret contents, host-file readability, effective JVM/Spring property overrides,
CPU/memory capacity, TLS/DNS, running containers or provider credentials/delivery.
Process/provider checks remain false in its output. It rejects malformed/deep or
over-4-MiB input without echoing it. Never capture unredacted `compose config`, full
`docker inspect`, env files or provider credentials as deployment/CI artifacts.

## Rehearsal before the window

Rehearse the candidate migrations against a private database backup using
[database recovery](database-recovery.md). For a retained full recovery set, its
nested database checkpoint can be used with the candidate checkout:

```sh
python3 "$hris_release/tool/recovery_set.py" verify "$hris_data_checkpoint" \
  --keys-directory "$hris_key_checkpoint"
python3 "$hris_release/tool/database_recovery.py" verify \
  "$hris_data_checkpoint/database" --migrate
```

These verifiers use new private services and never restore over the source or
start a worker against copied business records. Keep data/key packs outside Git
in separate private locations. Capture duration, data size, old-key verification,
schema/constraint/role checks and cleanup. Verify the retained image/configuration
identities, not just the source branch name. A passing small fictional exercise
does not establish the target dataset's restore time or capacity.

Review migration compatibility with the preceding application. Flyway migrations
are forward changes; an old image may be incompatible with the new schema or
immutable business snapshots. Decide before the window whether failure permits
image rollback, requires a forward repair, or requires a coordinated full restore.
Retained background jobs, mail and push deliveries must be included in this
decision because starting the worker can cause external effects.

## Maintenance window and final checkpoint

Put the target's reviewed edge route into maintenance and confirm it blocks new
browser/mobile commands. A company maintenance setting alone neither drains
already admitted requests nor stops background jobs. Stop other direct database/
storage/schema writers for the checkpoint window. Gracefully stop the existing
API, worker and Garage with the exact running project/overlay set:

```sh
"${hris_compose[@]}" stop api
"${hris_compose[@]}" stop worker
"${hris_compose[@]}" stop garage
```

API/worker grace periods are 90/120 seconds; investigate abnormal or OOM exits
before proceeding. Keep PostgreSQL running and retain all volumes. Do not use
`down -v`, a global prune, a new project name or a forced volume deletion.
Select new data/key checkpoint directories in the private locations recorded for
this window, then capture the stopped deployment:

```sh
python3 "$hris_release/tool/recovery_set.py" backup "$hris_data_checkpoint" \
  --keys-directory "$hris_key_checkpoint" --project "$hris_project"
```

Do not reuse an existing destination. Require the completed manifest and successful
isolated database/content/key verification, including cleanup. If capture fails,
keep the edge closed while deciding to repair the checkpoint or restart the unchanged
deployment. An unfinished checkpoint is not a rollback point. Before any candidate
schema change, rerun the candidate migration rehearsal against this final nested
database backup if the earlier rehearsal used a different checkpoint.

## Migration and controlled startup

With ingress still closed and the worker stopped, run only the candidate migration
task. Avoid a general `compose up` that would start every service before review:

```sh
"${hris_compose[@]}" run --rm --no-deps migrate
```

Require exit zero, successful Flyway history and the expected reviewed schema.
Stop on a failure; do not manually edit migration checksums or run `repair` to hide
it. Do not combine a PostgreSQL major-version upgrade, key rotation or storage
layout change with this application-only procedure without a separate rehearsal.

Start private dependencies and wait for actual readiness before API/worker:

```sh
"${hris_compose[@]}" up -d --no-deps garage antivirus-update antivirus
"${hris_compose[@]}" up -d --no-deps api
curl --fail --silent --show-error --max-time 10 \
  http://127.0.0.1:8088/actuator/health
```

Use the target's actual private API port. `pg_isready`, Garage health and a clamd
PING are separate checks; require current signatures and a clean/test-signature
inspection where approved. The API aggregate health includes its database
connection, but it does not validate every role, module or provider. A running
container or the presence of signature files alone is insufficient.

Switch the frontend to the retained candidate bundle using the target's reviewed
atomic asset switch. Confirm deep-route fallback and same-origin API routing while
maintenance still limits users. Verify an approved account through password/
provider login, MFA and company access, a scoped read, current-policy denial and
logout. Use authorized fictional acceptance data for any write/file/job canary;
record its IDs privately and remove only explicitly disposable evidence.

Starting the worker is a separate operational step: it resumes eligible jobs,
cleanup, mail and push. Confirm the intended provider credentials, queue scope and
recipients before doing so within the approved window:

```sh
"${hris_compose[@]}" up -d --no-deps worker
```

The worker has no HTTP server/actuator endpoint. Check stable process startup,
restricted worker DB access, lack of restart/OOM loops and actual approved job
progress/lease completion. Inspect bounded private logs without publishing account
or provider data. Startup flags only identify configured adapters. Real SMTP/FCM
delivery, Google identity exchange and device/APNs acceptance require their own
authorized recipients/devices; do not fabricate success from public Firebase config
or a process being up. Reopen ingress only after the release record's required
checks pass or its acceptance owner explicitly records a reviewed exception.

## Rollback and recovery decision

Keep ingress closed when a gate fails. Record whether migrations ran, whether
the candidate accepted writes and whether workers could have delivered externally.
Preserve bounded diagnostics and the checkpoint; do not delete candidate data to
make the old image start.

| State | Permitted recovery path after review |
| --- | --- |
| No schema/data change | Restart retained prior API/worker images and dashboard; verify before reopening. |
| Migrated, prior image explicitly compatible | Use the retained image override with `--no-deps`; keep the migrated database, verify contracts and then reopen. |
| Schema incompatible or data repair required | Keep ingress/workers stopped; perform the rehearsed coordinated restore or forward repair selected for this incident. |
| New writes or external delivery occurred | Review data loss and delivery reconciliation first. An old checkpoint cannot undo an email/push or preserve later writes. |

The repository verifiers intentionally have no live-restore destination. A real
restore needs the specific reviewed target-volume operation, matching database/
Garage checkpoint and retained keys, separate runtime/provider credentials, and
the old compatible artifacts. Never restore only PostgreSQL while leaving later
Garage content/metadata as if it were one checkpoint. Starting restored workers
requires explicit reconciliation of replayable pending work and external effects.

## Close the release record

Record UTC times for ingress closure, checkpoint completion, migration, startup,
canary results and reopening, plus the exact image/configuration identities and
approved outcome. Record observed outage/restore duration separately from target
RPO/RTO. Verify no fixture or replacement service remains accidentally exposed,
confirm backup custody/retention and keep the preceding recovery artifacts until
the target's policy permits retirement. Remove bootstrap access according to the
established identity process after independent administrator/MFA access is verified.
Do not remove retired encryption keys merely because this release succeeded.

Six deployment-tool regressions and actual Compose rendering of base, documents,
and documents-plus-push configurations passed with fictional values. That
validation covers static review/redaction, not execution of this live runbook.
