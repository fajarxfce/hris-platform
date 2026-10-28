# Reference workload measurements

The delivery targets are interactive API p95 below one second during attendance,
a 5,000-employee payroll below ten minutes, and bounded file/worker memory on a
four-vCPU/16-GiB deployment. Functional test counts and configured memory limits do
not establish those results. Each measurement must state its cohort, arrival rate,
resource limits, actual application artifacts and excluded operations.

## Attendance arrivals

`tool/measure_attendance.py` owns an internal Docker network, a private data volume
and actual PostgreSQL, API and worker containers. It has no existing-server URL or
database option. Source services and records are not measurement targets.

Use Linux with cgroup v2, Python 3.11+, Docker, at least four available CPUs and
enough free RAM/disk for the fixture. Build both JARs with Java 21 first. Run from
the repository root with Docker access as the same user:

```sh
./gradlew :apps:server:bootJar :apps:worker:bootJar
python3 tool/measure_attendance.py --employees 20 --arrival-seconds 10
python3 tool/measure_attendance.py --employees 5000 --arrival-seconds 900
```

`--cpus 0,1,2,3` selects exactly four available CPUs shared by all three containers;
change those IDs when needed. The driver uses the remaining CPUs when available
and records its affinity. A machine with only four CPUs shares them with the driver.
All application services run simultaneously within that shared set. Memory ceilings
are PostgreSQL 8 GiB, API 4 GiB and worker 4 GiB, with no additional swap allowance.
API/worker heaps are 2 GiB/1 GiB. PostgreSQL uses a disk-backed private Docker volume,
2 GiB shared buffers, 16 MiB work memory and 6 GiB effective cache estimate. It retains
normal durability settings and restricted application/worker roles. These settings
belong to this fixture and do not alter deployment defaults.

Setup creates a company and remote UTC shift through the public API with an enrolled
MFA administrator. Fictional accounts, employment/profile history and existing
session families are inserted into the owned database. The API creates one schedule;
the rest of the cohort receives its exact stored snapshot. SQL seeding is outside
the measured interval and supplies no live data. Password/Firebase login, bulk
onboarding and physical location evidence are outside this workload.

The full scenario offers 5,000 distinct employee arrivals over 900 seconds, or
5.556 arrivals/second. Each arrival makes four sequential real requests:

1. Refresh its existing native session through the public endpoint.
2. Acquire a device-bound attendance capture window.
3. Submit one online clock-in with a new operation and the current capture time.
4. Read that employee/day and require exactly one matching accepted event.

The resulting offered rate is approximately 22.222 HTTP requests/second. There is
one company, one shift, no existing attendance history and no simultaneous payroll,
document transfer or provider delivery. The real worker runs its enabled local
maintenance/synchronization loops. Refresh, three capture windows and thirty empty
day reads warm the first account before measurement; clock-in requests remain
included from their first execution. The ten-second rotation rule and capture-window
rate limit retain their normal behavior.

Arrival times follow an absolute schedule. At most 32 workflows are submitted at
once; late starts preserve their intended time. Reports distinguish endpoint elapsed
time, arrival lag and completion measured from the planned arrival, preventing a
backlogged driver from silently lowering the offered rate. The tool makes no hidden
retry. Failed requests keep their timing and safe failure category. HTTP bodies are
bounded at 2 MiB, request reads at thirty seconds and the whole arrival phase at the
configured duration plus 120 seconds. Cancellation stops further requests and joins
the owned executor. Cohorts are capped at 5,000 and arrival intervals at 1,800 seconds.

After the run, independent SQL checks require one accepted event, one employee,
one audit entry and one attendance synchronization head for every participant.
The report also requires all three services running within the configured CPU/memory
limits without OOM kills. Nearest-rank p50/p95/p99/max are recorded for each endpoint;
empty samples never count as passing. The latency flag additionally requires p95
below 1,000 ms for every endpoint and arrival-lag p95 below 100 ms. The command exits
unsuccessfully for a behavior/resource failure; a slower valid measurement remains
a report with `latencyTargetPassed: false`.

Successful teardown removes only resources bearing the generated run label. A
lost creation response still retains the resource name for cleanup. Unconfirmed
container removal preserves the data volume; foreign labels prevent deletion.
No global prune is used. Failed workflow diagnostics may remain in the printed
private temporary directory after container/volume removal. Remove that directory
only after checking its owned resources. Credentials and raw application logs are
never measurement artifacts.

The sanitized `.work/attendance-workload-<id>.json` contains endpoint distributions,
errors/counts, cgroup memory current/peak/events and cumulative CPU counters before
and after measurement, image IDs, source commit and both JAR hashes. Memory peaks
include startup and seeding; CPU deltas can isolate the arrival interval. The summary
is written only after cleanup succeeds. Preserve the report with its run context.

`./gradlew workloadToolCheck` runs the tool regressions, including slow-tail
percentiles, offered-schedule retention, bounded concurrency, cancellation/join,
failed/oversized HTTP responses and volume ownership. Full performance runs remain
explicit local exercises; the ordinary CI check does not claim a reference result.

## Observed attendance run — 28 October 2026

The [retained sanitized report](measurements/attendance-2026-10-28.json) records a
complete 5,000-employee/900-second run. All 20,000 requests succeeded; independent
SQL observed exactly 5,000 accepted events, distinct employees, audit entries and
attendance synchronization heads. The measured arrival phase took 899.938 seconds.

| Endpoint | Requests | p95 (ms) | p99 (ms) | Maximum (ms) |
| --- | ---: | ---: | ---: | ---: |
| Native refresh | 5,000 | 9.184 | 16.269 | 224.154 |
| Capture window | 5,000 | 15.676 | 21.323 | 68.165 |
| Clock-in | 5,000 | 18.908 | 25.858 | 75.736 |
| Attendance read | 5,000 | 93.753 | 112.676 | 341.198 |

Arrival-lag p95 was 0.207 ms (maximum 0.459 ms). Completion from the scheduled
arrival had p95 138.309 ms and maximum 402.826 ms. Every endpoint met the one-second
p95 target for this particular workload, and the driver kept the offered schedule.

| Service | Cgroup peak memory (MiB) | CPU seconds during measurement |
| --- | ---: | ---: |
| PostgreSQL | 634.94 | 444.684 |
| API | 720.53 | 108.587 |
| Worker | 478.05 | 9.509 |

Peaks include setup and are individual service peaks, not a simultaneous combined
peak. No service was OOM-killed. All containers used CPUs 0–3 with 16 GiB of summed
memory ceilings; the driver used CPUs 4–7. The shared VPS exposes eight virtual
CPUs on AMD EPYC 9V74 and approximately 32 GiB RAM. Its additional host memory and
storage/cache behavior remain different from a dedicated 16-GiB deployment.
The report retains exact container image IDs, JAR hashes and cgroup counters.

A preceding 20-employee smoke run also passed. Its initial fixture revisions found
an overly frequent warmup and an incorrectly seeded schedule representation; both
were corrected before the successful smoke and full runs. The schedule template
now comes from the actual API. Eight tool regressions, architecture and formatting
passed. Owned temporary
containers, network and data volume were removed after the full run. These are
local observations, with private HTTP and providers disabled.

## Payroll calculation

`python3 tool/measure_payroll.py --employees 2` runs a smoke exercise;
`--employees 5000` runs the complete cohort. The same owned deployment, CPU set,
memory/disk configuration and cleanup contracts apply. Do not run the attendance
and payroll measurements concurrently on those CPUs.

Setup closes actual workforce evidence through the worker. A separate preparer and
reviewer authenticate with MFA and use the public API to prepare/verify one
canonical compensation, monthly input and tax opening. The other fictional
employees receive those exact terms and contiguous draft/verified revisions through
SQL; every input references its own actual closed attendance. Database constraints
remain active. This is load-data preparation, not 5,000 individual human reviews.

The cohort has January resident/gross compensation, fixed earnings and configured
insurance, zero opening tax history and explicit paid-day decisions on otherwise
unassigned calendars. One public admission starts the real worker calculation.
An independent review actor polls once per second. Time includes admission, queue
wait, all retained employee calculations and observation of completion. Preparation,
human review, publication, finalization, payments and other compensation mixes are
outside that interval. Both preparation closing and calculation have twenty-minute
deadlines; failure does not silently retry or count as a passing fast result.

Independent SQL requires 5,000 distinct employees/results/audits and zero failures.
The homogeneous monetary results must reconcile exactly with decimal arithmetic.
The run and job must be `CALCULATED` and `SUCCEEDED`, with the resource contracts
intact. The retained report includes cumulative CPU counters, memory peaks, artifact
identities and separate preparation/admission/calculation durations.

The [28 October full report](measurements/payroll-2026-10-28.json) passed all these
checks. Preparation took 90.753 seconds; admission took 1.830 seconds; admission
through observed completion took **85.972 seconds** for 5,000 successful results.
The ten-minute target passed for this scenario. Gross/tax/take-home totals were
IDR 54,920,005,000.00 / 1,647,600,000.00 / 48,352,403,750.00, with 5,000 audits.
These fictional totals check consistency and do not replace statutory rule review.

| Service | Cgroup peak memory including setup (MiB) | CPU seconds during calculation |
| --- | ---: | ---: |
| PostgreSQL | 510.32 | 35.129 |
| API | 687.74 | 3.468 |
| Worker | 494.05 | 28.055 |

No OOM kills occurred and all owned resources were removed. A preceding two-employee
smoke completed calculation in 2.395 seconds. Eleven workload-tool tests now pass,
including failed/partial cohort rejection and exact cent reconciliation at large
aggregate values. The shared VPS/private HTTP limitations above also apply here.

## Document upload, inspection and download

`python3 tool/measure_documents.py --file-mib 100` exercises the complete document
path through the actual API, worker, PostgreSQL, Garage and ClamAV. Use the same
Linux/cgroup-v2/Docker/JDK prerequisites above. A smaller `--file-mib` (1–100) changes
the workload explicitly. The fixture requires outbound access only for the owned
FreshClam updater to acquire and verify official main/daily/bytecode databases.
It stops on provider refusal/cooldown; do not repeatedly rerun against a provider
that has asked the client to wait. No host port is published. Application services
use an internal network; the updater uses a separate owned outbound network.

All six services share the selected four CPUs. Memory ceilings total 16 GiB:
PostgreSQL 6 GiB, API 2 GiB, worker 2 GiB, Garage 1 GiB, scanner 3 GiB and updater
2 GiB. Both JVM heaps are 1 GiB. PostgreSQL settings otherwise match the earlier
fixture. Garage metadata/content and antivirus definitions use separate owned
volumes. Scanner/updater are non-root, read-only and capability-free, using the
checked-in daemon/update configuration. The scanner retains two threads, four
queued commands, a 60-second scan budget and 768 MiB temporary filesystem.
Earlier attendance/payroll fixture defaults are unchanged.

An MFA administrator creates a fictional company/employee and two document
declarations through the API. The driver generates exact-size PDFs with valid
cross-references and distinct deterministic uncompressed comment streams. Every
one-MiB chunk differs within and between the files; the independent database check
confirms this, preventing repeated-content deduplication from trivializing the
measurement. These are simple blank-page PDFs, not a representative mixed document
corpus, encrypted content or deeply nested archives.

The two uploads are offered concurrently through normal one-MiB chunk requests.
Two public validation admissions invoke the actual worker, storage reader, Tika
detector and ClamAV; revision status is polled every half second. Once both are
ready, four full downloads are offered concurrently, two per revision, and all
400 MiB are read incrementally and matched against source size/SHA-256. Finally,
the harmless standard EICAR string is uploaded through the same document path;
the worker must reject it as `document_unsafe` without publishing its revision.

The driver owns only the requested 1–4 transfer tasks and joins them on failure.
Each upload chunk allows thirty seconds, a download seventy seconds, each transfer
phase five minutes and validation four minutes. Binary response reads use 64 KiB
buffers; chunk JSON responses are capped at 2 MiB. No hidden retry occurs. A
failed or incomplete transfer cannot satisfy acceptance. Independent SQL requires
two ready/published matching PDFs, one unsafe rejection, three successful validation
jobs/audits and exactly 201 committed distinct chunk digests in the full scenario.
The resource contract also requires six running services, the intended CPU/memory
limits and no OOM kills. The report is written only after owned cleanup succeeds.

### Observed document run — 28 October 2026

The [first report](measurements/documents-2026-10-28-scan-limit.json) found a real
configuration mismatch: `MaxScanSize 150M` rejected both 100-MiB PDFs with
`Heuristics.Limits.Exceeded.MaxScanSize`. This scanner budget includes the original
input and extracted PDF streams. All chunks committed and the worker safely refused
publication; it did not establish successful file/download acceptance.

The checked-in daemon now uses a finite `MaxScanSize 250M`, providing extraction
headroom while preserving the 105-MiB per-file/stream limits, time/concurrency limits,
encrypted/exceeded-limit alerts and 3-GiB scanner ceiling. The
[second full report](measurements/documents-2026-10-28.json) passed:

| Phase | Observed result |
| --- | --- |
| Two 100-MiB uploads | 7.809 seconds; 200 chunk requests, p95 109.809 ms |
| Validation admission through observed readiness | 9.376 seconds; both `READY` |
| Four 100-MiB downloads | 3.841 seconds; all four size/SHA-256 checks passed |
| EICAR validation | 2.119 seconds; `REJECTED` / `document_unsafe` |
| Database checks | 201 distinct committed chunks, two publications, three audits/jobs |

FreshClam acquired and verified main 63 (3,287,027 signatures), daily 28167
(355,829 signatures, built 28 October 2026) and bytecode 339 (80 signatures).
ClamAV reported `1.5.4/28167`. The updater remained running during measurement;
its peak includes initial download/database verification. This observes initial
synchronization, not a later twelve-hour update/reload cycle.

| Service | Cgroup peak including setup (MiB) | CPU seconds during file phases |
| --- | ---: | ---: |
| PostgreSQL | 240.94 | 4.581 |
| API | 956.05 | 22.621 |
| Worker | 613.83 | 4.133 |
| Garage | 170.69 | 6.919 |
| Scanner | 1,713.70 | 12.621 |
| Signature updater | 673.52 | 0.120 |

Peaks are individual cgroup peaks, not a simultaneous combined peak. No service
was OOM-killed. All owned containers, two networks, four volumes and successful
private fixture data were removed; the failed-run directory was removed after
diagnosis. The report records exact image/JAR identities, configuration hashes,
source commit and cgroup counters. The actual APIs/JARs required no source change.
The fixture configuration and tools belong to the increment retaining this report.

Sixteen workload-tool regressions passed, including distinct PDF chunks/valid
offsets, truncated or changed downloads, deadlines despite progress, cancellation
with joined siblings and lost signature-volume creation. The service configuration
check also retains its separate minimal-signature health/clean/EICAR exercise.

## Acceptance limits

A CPU set and memory ceilings constrain this local experiment. A shared VPS still
has different CPU contention, storage, network/TLS costs and kernel/cache behavior
from a dedicated reference deployment. Record those differences when interpreting
results. These measurements cover the documented attendance, payroll calculation
and full-signature document scenarios separately. Mixed simultaneous workloads,
other payroll/document corpora, phone/network latency, later signature reloads and
general capacity across HRIS modules require their own observed runs.
