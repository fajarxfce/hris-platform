# Elapsed-time export retention acceptance

`ReportExportElapsedRetentionTest` is an opt-in, bounded 75-minute worker/PostgreSQL/
Garage acceptance case. Normal test runs skip it unless
`HRIS_RUN_ELAPSED_RETENTION=true`. Run it separately from other worker tests:

```sh
HRIS_RUN_ELAPSED_RETENTION=true ./gradlew --no-daemon :apps:worker:test --tests '*ReportExportElapsedRetentionTest'
```

Use JDK 21 and Docker access as described in [agent handoff](agent-handoff.md#commands-and-environment).
Do not run another `:apps:worker:test` or rebuild its classes/JARs in the same checkout
while this case owns its JVM/report files. Other feature work can use an isolated
Git worktree. Testcontainers and the Spring context own connection/client/container
cleanup on success, failure and interruption. No live deployment is used.

The fixture creates fictional company/account/job identities, then uses normal
repositories, snapshot encoding, upload reservations, real S3 PUT/read, part
acceptance, publication and job completion. It sets a short initial three-minute
export lifetime through the existing persistence contract; public admission retains
its normal seven-day lifetime. The one-hour deletion grace is unchanged. A control
export has three hours of lifetime. The expired export reserves an abandoned object
and one accepted CSV part; the control keeps one accepted part. Both owners are
then deactivated.

No database clock, deadline or trigger is changed after creation. A future
application clock must not cause premature database cleanup. After actual expiry,
the worker's production retention use case removes snapshot rows while objects and
retention quota remain. Once per minute throughout the real hour, the test verifies
that garbage collection selects nothing, all three S3 objects exist, original
metadata remains and the expired owner's quota stays reserved.

After the original grace ends, real S3 deletion removes the expired objects. An
adapter deliberately loses one successful delete result; its queue receipt remains
pending and must continue blocking retirement even though the object is already
absent. The ordinary five-second retry deletes the absent object idempotently.
The test waits for the actual postponed retirement schedule, then requires one
retirement audit, zero expired quota, retained immutable header/snapshot evidence,
no restored account access, and unchanged live export/rows/exact S3 content.
This loss is injected at the repository response boundary, not an FCM/network test.
The production worker use cases run in the real worker application context with
its non-bypass runtime role; scheduled timers are disabled for explicit ownership.

Progress and final results are written atomically to
`apps/worker/build/reports/elapsed-retention.json`. It contains phase/timing/counts
only. The JUnit XML/report supplies the final test outcome. A waiting progress file
or a passing compilation is not an acceptance pass.

## Executed result — 29 October 2026

The opt-in acceptance passed at compiled Platform `ecb17627654a8e52555f9fa6542b29938254aad0`
(schema 100). Source expiry was `2026-10-29T02:16:08.258929Z`; the unchanged physical
grace ended at `2026-10-29T03:16:08.258929Z`. The final observation was
`2026-10-29T03:17:16.390452Z`. JUnit records one test, zero failures/errors/skips,
3,860.892 seconds; Gradle completed successfully in 1h4m32s.

The entire real hour retained the quota and all three objects. After eligibility,
two expired objects were deleted and a lost successful delete response recovered
through the ordinary retry. The scheduled retirement then released the expired
quota exactly once, with one retirement audit and the original immutable header.
Two expired snapshot rows were removed; both live rows, exact live object bytes,
live quota and deactivated account states remained unchanged. Final maintenance
was a no-op. Testcontainers and the worker context closed their owned resources.

[Tracked measurements](measurements/export-retention-elapsed-2026-10-29.json)
contain the sanitized final progress and JUnit outcome. Local
`.work/export-retention-elapsed.log` and the worker XML remain supplementary.
No clock/deadline/trigger was rewritten after creation. The three-minute fixture
lifetime does not change public seven-day retention, and the deliberate response
loss is at the storage repository boundary. This validates physical expiry and
retirement; other provider/device acceptance remains separate.
