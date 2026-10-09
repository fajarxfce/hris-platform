package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.*
import dev.fajar.hris.workforce.domain.usecases.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

class WorkPeriodHttpTest : WorkPeriodApiFixture() {
    @Autowired private lateinit var advance: AdvanceWorkPeriodClose
    @Autowired private lateinit var abort: AbortWorkPeriodClose
    @Autowired private lateinit var overtime: OvertimeRepository
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var attendance: AttendanceRepository
    @Autowired private lateinit var corrections: AttendanceCorrectionRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var runtime: JdbcTemplate

    @Test
    fun closeReplaysOneJobAndCompetingFinalizersPreserveOneImmutableSnapshot() {
        val f = fixture()
        val key = UUID.randomUUID()
        val first = close(f, key = key)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(first.body(), close(f, key = key).body())
        assertEquals(409, close(f).statusCode())
        assertEquals(422, close(f, month = "2026-10").statusCode())
        val lease = claim().single()
        assertEquals(Result.Success(JobStep(1, false)), advance.execute(f.actor, lease))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val calls =
                (1..2).map {
                    pool.submit<Result<JobStep>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        advance.execute(f.actor, lease)
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = calls.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
        }
        assertEquals("CLOSED", period(f).get("status").asString())
        val response = get(f.employeeClient, "${f.path}/periods/2026-09/employees/${f.employee}")
        assertEquals(200, response.statusCode(), response.body())
        val snapshot = json.readTree(response.body())
        assertEquals(30, snapshot.get("days").size())
        assertEquals("OFF", snapshot.get("days")[0].get("fact").asString())
        assertEquals("UNASSIGNED", snapshot.get("days")[1].get("fact").asString())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and action='workforce.period_closed'",
                    Int::class.java,
                    f.company,
                ),
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update work_periods set status='OPEN',closed_at=null where company_id=?",
                    f.company,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from work_period_snapshots where company_id=?", f.company)
        }
        val other = fixture()
        assertEquals(
            404,
            get(other.admin, "${other.path}/periods/2026-09/employees/${f.employee}").statusCode(),
        )
    }

    @Test
    fun calendarGatesBothHolidayDatesAndLateEvidenceCannotRewriteTheClosedPeriod() {
        val f = fixture()
        val lease = begin(f)
        val late = capture(f)
        assertEquals(
            lease.job.request.id,
            database()
                .queryForObject(
                    "select closing_job_id from attendance_events where id=?",
                    UUID::class.java,
                    late,
                ),
        )
        assertEquals(409, reject(f, late).statusCode())
        val updates =
            listOf(
                "${f.path}/employees/${f.employee}/roster/2026-09-30" to
                    """{"reason":"Change roster"}""",
                "${f.path}/employees/${f.employee}/schedule" to
                    """{"effectiveFrom":"2026-09-30","days":{},"reason":"Change pattern"}""",
                "${f.path}/holidays/${f.holiday}" to
                    """{"workDate":"2026-10-01","name":"Moved holiday","expectedVersion":0,"reason":"Move holiday"}""",
            )
        for ((path, body) in updates) {
            val result = command(f.admin, path, body, f.csrf, UUID.randomUUID(), "PUT")
            assertEquals(409, result.statusCode(), result.body())
            assertEquals("work_period_locked", json.readTree(result.body()).get("code").asString())
        }
        assertEquals(Result.Success(JobStep(1, false)), advance.execute(f.actor, lease))
        assertEquals(Result.Success(JobStep(2, true)), advance.execute(f.actor, lease))
        val before =
            get(f.employeeClient, "${f.path}/periods/2026-09/employees/${f.employee}").body()
        assertEquals(200, reject(f, late).statusCode())
        val newLate = capture(f)
        val acceptance =
            command(
                f.admin,
                "${f.path}/attendance/$newLate/review",
                """{"version":0,"decision":"ACCEPT","reason":"Late request"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(409, acceptance.statusCode(), acceptance.body())
        assertEquals(
            before,
            get(f.employeeClient, "${f.path}/periods/2026-09/employees/${f.employee}").body(),
        )
    }

    @Test
    fun cancellationPreservesEvidenceAndANewAttemptMustResolveOldPendingEvidence() {
        val f = fixture()
        val first = begin(f)
        val raw = capture(f)
        cancel(f, first)
        assertEquals(
            Result.Success(Unit),
            abort.execute(first, Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
        )
        assertEquals("REVIEW_REQUIRED", period(f).get("status").asString())
        assertEquals(
            "CANCELLED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    first.job.request.id,
                ),
        )
        val second = begin(f, period(f).get("version").asLong())
        val rejected = advance.execute(f.actor, second)
        assertTrue(rejected is Result.Failed, rejected.toString())
        assertEquals("attendance_verification_required", (rejected as Result.Failed).failure.code)
        assertEquals(Result.Success(Unit), abort.execute(second, rejected.failure))
        assertEquals(200, reject(f, raw).statusCode())
        val third = begin(f, period(f).get("version").asLong())
        assertEquals(Result.Success(JobStep(1, false)), advance.execute(f.actor, third))
        assertEquals(Result.Success(JobStep(2, true)), advance.execute(f.actor, third))
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select count(*) from background_jobs where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun expiredLeaseGuardRollsBackTheSnapshotAndTheCheckpointTogether() {
        val f = fixture()
        val lease = begin(f)
        val expiring =
            object : WorkPeriodRepository by periods {
                override fun saveSnapshot(
                    companyId: UUID,
                    jobId: UUID,
                    snapshot: WorkPeriodSnapshot,
                ): Result<Unit> =
                    periods.saveSnapshot(companyId, jobId, snapshot).map {
                        // Force elapsed database time at the commit guard, inside the same
                        // transaction.
                        runtime.update(
                            "update background_jobs set lease_until=now()-interval '1 second' where id=?",
                            jobId,
                        )
                        Unit
                    }
            }
        val guarded =
            AdvanceWorkPeriodClose(
                expiring,
                jobs,
                schedules,
                attendance,
                corrections,
                journal,
                transactions,
                clock,
                overtime,
            )
        val outcome = guarded.execute(f.actor, lease)
        assertTrue(outcome is Result.Failed, outcome.toString())
        assertEquals("job_lease_lost", (outcome as Result.Failed).failure.code)
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from work_period_snapshots where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select completed_items from background_jobs where id=?",
                    Int::class.java,
                    lease.job.request.id,
                ),
        )
        assertEquals(Result.Success(JobStep(1, false)), advance.execute(f.actor, lease))
    }

    @Test
    fun failedFinalAuditCannotPartiallyCloseThePeriodOrFinishTheJob() {
        val f = fixture()
        val lease = begin(f)
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        database()
            .execute(
                """CREATE FUNCTION fail_period_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
            IF NEW.action='workforce.period_closed' THEN RAISE EXCEPTION 'fixture failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$"""
            )
        database()
            .execute(
                "CREATE TRIGGER fail_period_audit BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION fail_period_audit()"
            )
        try {
            assertTrue(advance.execute(f.actor, lease) is Result.Failed)
            assertEquals("PROCESSING", period(f).get("status").asString())
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select completed_items from background_jobs where id=?",
                        Int::class.java,
                        lease.job.request.id,
                    ),
            )
            assertEquals(
                "RUNNING",
                database()
                    .queryForObject(
                        "select status from background_jobs where id=?",
                        String::class.java,
                        lease.job.request.id,
                    ),
            )
        } finally {
            database().execute("DROP TRIGGER fail_period_audit ON audit_entries")
            database().execute("DROP FUNCTION fail_period_audit()")
        }
        assertEquals(Result.Success(JobStep(2, true)), advance.execute(f.actor, lease))
    }

    @Test
    fun exhaustedWorkerRequiresExplicitRecoveryBeforeTheMonthCanBeChanged() {
        val f = fixture()
        val lease = begin(f)
        val version = period(f).get("version").asLong()
        fun recover() =
            command(
                f.admin,
                "${f.path}/periods/2026-09/recover",
                """{"expectedVersion":$version,"reason":"Worker recovery"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(409, recover().statusCode())
        database()
            .update(
                "update background_jobs set attempts=8,lease_until=now()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        assertTrue(claim().isEmpty())
        assertEquals("PROCESSING", period(f).get("status").asString())
        val recovered = recover()
        assertEquals(200, recovered.statusCode(), recovered.body())
        assertEquals("REVIEW_REQUIRED", period(f).get("status").asString())
        assertEquals("job_attempts_exhausted", period(f).get("failureCode").asString())
        assertEquals(
            Result.Success(Unit),
            abort.execute(lease, Failure(FailureKind.UNEXPECTED, "late_failure")),
        )
        assertEquals("REVIEW_REQUIRED", period(f).get("status").asString())
    }
}
