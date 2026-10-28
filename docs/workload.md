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

## Acceptance limits

A CPU set and memory ceilings constrain this local experiment. A shared VPS still
has different CPU contention, storage, network/TLS costs and kernel/cache behavior
from a dedicated reference deployment. Record those differences when interpreting
results. These measurements do not establish payroll throughput, full-signature
document inspection/upload memory, phone/network latency or general capacity across
all HRIS modules. Those workloads require their own observed runs.
