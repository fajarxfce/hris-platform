# Agent continuation — HRIS workspace

Updated 28 October 2026 (UTC). **The overall PRD is still in progress.** The user
asked to continue through the remaining PRD, push each commit, and keep continuation
notes in Git. Read this file first, then [completion](completion.md),
[product](product.md), the relevant module contract and each repository's `AGENTS.md`.

## Workspace and delivery policy

The workspace is `/home/fajar/hris` with three sibling Git repositories:

| Repository | Branch | Last completed/pushed increment |
| --- | --- | --- |
| `hris-platform` | `feat/firebase-auth` | Payroll measurement/handoff below; previous attendance baseline `51c4b1b3` |
| `hris-mobile` | `feat/native-push` | `60456c48` — Android URL/inbox archival acceptance |
| `compose-fluent-starter` | `fix/push-token-lifecycle` | `a16325ef` — existing validated foundation |

The current commit contains the payroll probe, accepted report and handoff.
Inspect `git status`, `git log` and
the upstream before continuing. Mobile and Starter were clean at this update.

Mobile was generated from Starter and is the proving ground. Validate reusable
foundation corrections in Mobile before porting them to Starter. HRIS business
features stay in Mobile/Platform. The latest work changes operational tools and
acceptance records; it requires no Starter port.

The user has authorized continued implementation and a push after every atomic
commit. Do not ask again for routine edits, tests, commits or pushes. Keep the
implementation, relevant tests and documentation together. Preserve unrelated work,
do not add co-author trailers, and do not infer release/live deployment permission
from a Git push. Completing an increment does not complete the PRD.

`/home/fajar/hris/PRD-PROGRESS.md` and `REPO-TRACE.md` are local supplementary notes
outside these Git repositories. The tracked documents linked here are the durable
handoff; a fresh clone must not depend on those local files or previous chat history.

## Current checkpoint: payroll workload passed

The executable probe is in [measure_payroll.py](../tool/measure_payroll.py), with
fictional source preparation in [payroll_workload_data.py](../tool/payroll_workload_data.py).
It shares the tested [owned deployment fixture](../tool/workload_fixture.py) and
bounded HTTP client with the attendance measurement.

**Observed:** the two-employee smoke passed. Preparation took 24.885 seconds and
actual calculation 2.395 seconds. Both results succeeded, the amounts reconciled,
and the resource/cleanup checks passed. Evidence on this machine:

- `.work/payroll-workload-smoke.log`
- `.work/payroll-workload-90d6510ddbb64138bb740c0d216c318d.json`

**Full run passed:** the [tracked report](measurements/payroll-2026-10-28.json)
records all 5,000 successful retained results, distinct employees and audits.
Preparation took 90.753 seconds; admission took 1.830 seconds; admission through
observed calculation completion took 85.972 seconds. There were no result failures
or OOM kills, and the run/job finished `CALCULATED`/`SUCCEEDED`. Decimal totals
reconciled. All owned containers, network, data volume and private directory were
removed. No workload is active at this update.

The ten-minute target passed for this documented calculation scenario. The raw
local evidence is `.work/payroll-workload-5000.log` and
`.work/payroll-workload-a092a59411564060b298b5508b672969.json`. Eleven tool tests
passed, including rejection of fast-but-failed/partial outcomes and one-cent
aggregate discrepancies. Architecture and formatting also passed. Inspect retained evidence before repeating a passing run;
rerun when a change or unresolved concern warrants it.

If later work may have started another run, inspect the process/log first:

```sh
pgrep -af '^python3 tool/measure_payroll.py'
tail -n 30 .work/payroll-workload-5000.log
```

The probe first closes actual workforce evidence through the worker. A distinct
preparer and reviewer authenticate/enroll MFA and create/verify one canonical
compensation/input/January tax-opening example through the API. The rest of the
fictional cohort receives those exact terms and contiguous draft/verified history
through SQL, with each input referencing its own actual closing. No constraints
are disabled. The timed phase starts before public payroll admission and ends
when the independent review read observes the completed run. Polling is once per
second; preparation and calculation each have a twenty-minute bound.

This is a homogeneous January resident/gross-pay calculation: fixed earnings,
configured insurance, zero opening balances and explicit paid-day decisions.
It measures calculation admission/worker completion. Human review, finalization,
publication, payments, annual reconciliation and other compensation mixes remain
outside that measurement. The probe never submits bank instructions or contacts
external providers.

Next actions:

1. Continue with the actual file/scanner workload and full signature/data memory.
   Existing `tool/check_document_services.py` validates service behavior with a
   minimal owned signature database; it does not supply the full workload result.
2. Continue worker integration configuration/health and the deployment/cutover
   runbook, followed by the remaining native/provider acceptance below.
3. For any new exercise, distinguish measured success from setup failures. Inspect
   relevant private diagnostics, fix the actual boundary/fixture, verify cleanup
   and retain only sanitized evidence in Git.
4. Update [workload](workload.md), [delivery](delivery.md), [completion](completion.md)
   and this handoff with the next observed result. Commit and push each increment.

## Completed in the latest work

### Attendance workload — `51c4b1b3`

The [tracked report](measurements/attendance-2026-10-28.json) records the complete
5,000-employee/900-second run: 20,000 successful HTTP requests, no errors, exactly
5,000 accepted events, distinct employees, audits and attendance sync heads.
Native refresh/window/clock-in/read p95 values were 9.184/15.676/18.908/93.753 ms.
Scheduled-completion p95 was 138.309 ms and arrival-lag p95 0.207 ms.

Actual API/worker/PostgreSQL shared CPUs 0–3 and memory ceilings totalling 16 GiB;
the driver used 4–7. Individual cgroup peaks including setup were
720.53/478.05/634.94 MiB, without OOM kills. The shared host has eight virtual CPUs
and approximately 32 GiB RAM (AMD EPYC 9V74), so this is a constrained local result,
not an assertion about every dedicated deployment. Private HTTP and the exact
workload exclusions are documented in [workload](workload.md).

A corrected twenty-employee smoke, eight tool tests, architecture and formatting
passed. Initial fixture errors were an excessive capture-window warmup and an
incorrectly seeded schedule shape; both were corrected before the full run. Source
application behavior needed no change. All owned resources and failed-run private
diagnostics were cleaned. Logs: `.work/attendance-workload-5000.log` and
`.work/workload-tool-checks.log`.

### Database/content/key recovery — `a9b4d105`

[Recovery sets](recovery-set.md) capture a stopped API/worker/Garage deployment with
PostgreSQL running. Data and key packs use separate private directories outside
Git. The verifier restores isolated PostgreSQL/Garage copies, checks every object
and decrypts retained identity envelopes with the actual application cipher.
It never starts application processes against restored personnel records.

Two complete real fictional API/worker/PostgreSQL/Garage exercises passed. The
final run verified schema 97, old/current identity keys, two encrypted records and
104 objects totalling 2,098,795 bytes. Later source changes did not alter the
checkpoint; a wrong retired key failed despite a recomputed manifest checksum.
Twenty recovery-tool tests, 21 existing database-tool tests and three JVM cipher
tests passed, alongside API packaging, architecture and formatting. Owned resources
and failed fixture directories were removed. The CI exercise is configured; no
hosted CI success is claimed for these branch pushes.

Logs: `.work/recovery-set-integration-final.log`, `.work/recovery-final-checks.log`,
`.work/recovery-key-verifier.log`. The schema ceiling in `tool/recovery_set.py` is
deliberately 97: review new persisted encryption contracts before raising it.

### Native acceptance and integrations

- Mobile `1c1f9104` / Platform `893db26a`: actual Android 35/API/PostgreSQL/worker
  Pending Actions acceptance. An edited leave draft survived offline process
  restart; pristine forms counted zero, local summary refresh made no HTTP request,
  reconnect retained the text and explicit deletion cleared it. No company writes
  occurred. This proves the tested draft path, not every possible pending status.
- Mobile `60456c48` / Platform `839505cf`: Android VIEW/BROWSABLE URLs passed cold
  login/company gating, repeated/resumed navigation, account/company/query rejection,
  offline message restoration and server archival retained after offline restart.
  Read/acknowledgement remained null and no company writes occurred. It does not
  establish FCM delivery, verified HTTPS links, iOS or physical-device acceptance.
- Platform `1f95e0c3`: six API integration startup flags and the localized dashboard
  passed global-permission/MFA and three-account revocation acceptance. The API
  projection acquires no provider identifiers/secrets. Validation included 184
  backend tests, the complete worker graph, 3,922 dashboard tests and seventeen
  distinct Chromium cases. Worker configuration and provider health are separate.
- Platform `aaa596a0`: real API/PostgreSQL/worker/private Garage/browser export
  acceptance passed 1,201-row multipart downloads, exact bytes/ranges, immutable
  captured source, owner/source permissions and lost-admission receipt recovery.

Mobile evidence is tracked in its `docs/validation.md` and `docs/deep-links.md`;
Platform contracts/evidence are linked from [completion](completion.md).

## Remaining delivery areas

Use the product/module contracts as the acceptance baseline; this is a continuation
order, not a claim that every module criterion has been exhaustively audited.

1. Measure the actual file/scanner workload, including full signature/data memory, and retain the
   distinction between this VPS experiment and a dedicated reference deployment.
2. Worker integration configuration/health evidence and the complete deployment/
   cutover runbook remain. API startup flags do not prove worker configuration or
   provider delivery; isolated recovery does not establish a live cutover/RPO/RTO.
3. Native/provider acceptance still includes actual Google identity exchange,
   FCM registration/delivery and the remaining iOS/physical-device cases. Read the
   current Mobile validation/native-push documents before choosing the next case.
4. Reconcile the remaining module criteria in [completion](completion.md) with
   their individual contracts/evidence. Do not infer completion from routes,
   repository size, a test total, a passing build or an old summary paragraph.

## Firebase state and external inputs

The project is `cmp-fajar`; the user enabled Email/password and Google and wants
both Firebase Authentication and FCM. Android configurations exist locally at
`apps/android/google-services.json` in Mobile and Starter. They are ignored/private;
do not print or commit them. The public Google web client ID is
`415338369394-nop83je85ci1btt097rpl21r6sm96ldv.apps.googleusercontent.com`.

Actual email/password Firebase-to-HRIS native login, HRIS MFA, company selection
and logout were accepted; the temporary Firebase user was removed. Google account
chooser/Back was accepted, but a real Google ID-token exchange was not. The last
known missing external inputs were worker FCM service-account configuration,
iOS Firebase/APNs/device setup and the remaining real-provider/device acceptance.
Recheck local availability before asking for anything already supplied. Public
Firebase Android configuration alone does not grant Firebase project-management
or FCM server credentials. Do not invent unavailable acceptance evidence.

## Commands and environment

Use JDK 21 (`/usr/lib/jvm/java-21-openjdk`). On this VPS Docker access uses the
existing Docker group, for example:

```sh
sudo -n runuser -u fajar -g docker -- env \
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/local/bin:/usr/bin:/bin \
  python3 tool/measure_payroll.py --employees 5000
```

Only rerun after confirming the preceding workload has stopped. Both JARs must
exist at `apps/server/build/libs/apps-server.jar` and
`apps/worker/build/libs/apps-worker.jar`. The application project is `:apps:server`,
not `:apps:api`; the Gradle task is `architectureCheck`, not `checkArchitecture`.

```sh
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  PATH=/usr/lib/jvm/java-21-openjdk/bin:/usr/local/bin:/usr/bin:/bin \
  ./gradlew --no-daemon workloadToolCheck architectureCheck formatCheck
```

Use `--no-daemon` for builds/tests needing the Docker group; an older daemon may
retain different groups. Avoid concurrent heavy builds/workloads on the measurement
CPUs. Docker fixtures track owned resources using the `hris.recovery-run` label:

```sh
docker ps -a --filter label=hris.recovery-run
docker network ls --filter label=hris.recovery-run
docker volume ls --filter label=hris.recovery-run
```

Inspect ownership before cleanup. Failed cleanup deliberately retains private
data needed by a remaining container; never use a global Docker prune. Successful
reports are written only after the probe confirms its own cleanup.

For Mobile builds use `ANDROID_HOME=/home/fajar/Android/Sdk` and
`:apps:android:assembleDevDebug`. The dedicated emulator is AVD `hris-mobile-dev`,
serial `emulator-5560`; preserve its data and never use `-wipe-data`. The latest
acceptance used an isolated API override, not a source change to the standard
WireGuard origin. Physical phone delivery has been install-only: notify before
installing and do not drive phone UI. Read Mobile's own instructions first.

Keep this handoff current in the commit that records the next result. Summarize
failures honestly, name the corrected cause and keep the final validated state
easy to distinguish from historical attempts.
