package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveAccrualScheduleHttpTest : LeaveAccrualScheduleApiFixture() {
    @Test
    fun reviewedMonthlyPeriodsRunOnlyWhenDueAndExposeCurrentProgress() {
        val at = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(1)
        val zone = ZoneId.of("Asia/Jakarta")
        val firstPeriod = YearMonth.from(at.atZone(zone)).minusMonths(1)
        val f = scheduledFixture(at = at)
        val review =
            accountingBody(
                get(
                    f.admin,
                    "/api/v1/companies/${f.company}/leave/accrual-schedules/review?typeId=${f.type}&firstPeriod=$firstPeriod&periods=3",
                )
            )
        assertEquals("Asia/Jakarta", review["timezone"].asString())
        assertEquals(3, review["periods"].size())
        val firstDueAt =
            at.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant()
        assertEquals(firstDueAt, Instant.parse(review["periods"][0]["dueAt"].asString()))
        val input = scheduleCommand(f, first = firstPeriod.toString())
        val key = UUID.randomUUID()
        val first = submitSchedule(f, input, key)
        accountingBody(first)
        assertEquals(first.body(), submitSchedule(f, input, key).body())
        val initial = scheduleView(f, input.id)
        assertEquals(3, initial["periods"].size())
        assertEquals(at, Instant.parse(initial["periods"][0]["scheduledFor"].asString()))
        assertTrue(initial["periods"].all { it["jobStatus"].asString() == "QUEUED" })
        assertEquals(0, accountingRows(f, "leave_accrual_postings"))
        val lease =
            claimDocuments(JobKind.LEAVE_ACCRUAL).single { it.job.request.companyId == f.company }
        assertEquals(
            initial["periods"][0]["batch"]["jobId"].asString(),
            lease.job.request.id.toString(),
        )
        drainBatch(f, lease)
        val completed = getSchedule.execute(accountingActor(f), input.id)
        assertTrue(completed is Result.Success, completed.toString())
        val detail = (completed as Result.Success).value
        assertEquals(JobStatus.SUCCEEDED, detail.periods.first().jobStatus)
        assertEquals(1, detail.periods.first().completedEmployees)
        assertTrue(detail.periods.drop(1).all { it.jobStatus == JobStatus.QUEUED })
        assertEquals(1, accountingRows(f, "leave_accrual_postings"))
        assertTrue(
            claimDocuments(JobKind.LEAVE_ACCRUAL).none { it.job.request.companyId == f.company }
        )
    }

    @Test
    fun annualPeriodsRetainTheSelectedEligibilityMonthAndKnownPolicyRevisions() {
        val f = scheduledFixture("ANNUAL")
        accountingBody(
            accountingPolicy(f, version = 1, frequency = "ANNUAL", days = "12", from = "2027-01-01")
        )
        val review = reviewSchedule.execute(accountingActor(f), f.type, YearMonth.of(2026, 11), 2)
        assertTrue(review is Result.Success, review.toString())
        val periods = (review as Result.Success).value.periods
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2027, 11)),
            periods.map { it.period },
        )
        assertEquals(listOf(2, 24), periods.map { it.type.policy.accrual!!.halfDaysPerPeriod })
        accountingBody(
            submitSchedule(f, scheduleCommand(f, first = "2026-11", periods = 2, version = 2))
        )
        assertEquals(2, accountingRows(f, "leave_batches"))
        assertEquals(0, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun futureCancellationAndRecoveryCannotBypassTheOriginalDueDate() {
        val at = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(1)
        val f = scheduledFixture(at = at)
        val firstPeriod = YearMonth.from(at.atZone(ZoneId.of("Asia/Jakarta"))).plusMonths(1)
        val input = scheduleCommand(f, first = firstPeriod.toString(), periods = 1)
        accountingBody(submitSchedule(f, input))
        val period = scheduleView(f, input.id)["periods"][0]
        val batch = UUID.fromString(period["batch"]["id"].asString())
        val jobId = UUID.fromString(period["batch"]["jobId"].asString())
        val job = accountingBody(get(f.admin, "/api/v1/companies/${f.company}/jobs/$jobId"))
        accountingBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/jobs/$jobId/cancel",
                json.writeValueAsString(mapOf("expectedVersion" to job["version"].asLong())),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        )
        val cancelled = claimDocuments(JobKind.LEAVE_ACCRUAL).single { it.job.request.id == jobId }
        assertEquals(
            Result.Success(Unit),
            abortBatch.execute(
                cancelled,
                Failure(FailureKind.CONFLICT, "job_cancellation_requested"),
            ),
        )
        accountingBody(resumeBatch(f, batch, 1))
        val recovered = scheduleView(f, input.id)["periods"][0]
        assertNotEquals(jobId.toString(), recovered["batch"]["jobId"].asString())
        assertEquals(period["scheduledFor"], recovered["scheduledFor"])
        assertTrue(
            claimDocuments(JobKind.LEAVE_ACCRUAL).none { it.job.request.companyId == f.company }
        )
        val recoveredId = UUID.fromString(recovered["batch"]["jobId"].asString())
        val retainedJob =
            transactions.run(accountingActor(f)) { batchJobs.find(f.company, recoveredId, false) }
        assertTrue(retainedJob is Result.Success, retainedJob.toString())
        assertEquals(
            Instant.parse(period["scheduledFor"].asString()),
            (retainedJob as Result.Success).value!!.request.scheduledFor,
        )
        assertEquals(0, accountingRows(f, "leave_accrual_postings"))
    }

    @Test
    fun competingSchedulesShareTheExistingActivePeriodGuard() {
        val f = scheduledFixture()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val actor = accountingActor(f)
        val commands = listOf(scheduleCommand(f), scheduleCommand(f))
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                commands.map { input ->
                    pool.submit<Result<MutationReceipt>> {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        scheduleAccrual.execute(actor, UUID.randomUUID(), input)
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = pending.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            failure(results.single { it is Result.Failed }, "leave_batch_active")
        }
        assertEquals(1, accountingRows(f, "leave_accrual_schedules"))
        assertEquals(3, accountingRows(f, "background_jobs"))
    }

    @Test
    fun admissionReservesCapacityForTheEntirePlanOrNothing() {
        val f = scheduledFixture()
        val actor = accountingActor(f)
        repeat(8) { index ->
            val command =
                scheduleCommand(
                    f,
                    first = YearMonth.of(2026, 10).plusMonths(index * 12L).toString(),
                    periods = 12,
                )
            assertTrue(scheduleAccrual.execute(actor, UUID.randomUUID(), command) is Result.Success)
        }
        val next = scheduleCommand(f, first = "2034-10", periods = 3)
        assertTrue(scheduleAccrual.execute(actor, UUID.randomUUID(), next) is Result.Success)
        val before = scheduleRows(f)
        failure(
            scheduleAccrual.execute(
                actor,
                UUID.randomUUID(),
                scheduleCommand(f, first = "2035-01", periods = 2),
            ),
            "job_queue_full",
        )
        assertEquals(before, scheduleRows(f))
        assertTrue(
            scheduleAccrual.execute(
                actor,
                UUID.randomUUID(),
                scheduleCommand(f, first = "2035-01", periods = 1),
            ) is Result.Success
        )
        assertEquals(100, accountingRows(f, "background_jobs"))
    }

    @Test
    fun maximumReviewedCohortAndPeriodCountFitTheExistingTransactionBudget() {
        val f = scheduledFixture()
        database()
            .update(
                """with inserted as (
            insert into persons(id,owner_company_id,legal_name,nationality)
            select gen_random_uuid(),?,'Accrual fixture employee','ID' from generate_series(1,4998)
            returning id)
            insert into employments(company_id,id,person_id,employee_number)
            select ?,gen_random_uuid(),id,replace(id::text,'-','') from inserted""",
                f.company,
                f.company,
            )
        database()
            .update(
                """insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,
            start_date,end_date,status,branch_id,department_id,position_id,cost_center_id,manager_id,actor_id,reason)
            select e.company_id,e.id,0,r.effective_from,r.contract_kind,r.start_date,r.end_date,r.status,
                r.branch_id,r.department_id,r.position_id,r.cost_center_id,r.manager_id,r.actor_id,'Accrual fixture employment'
            from employments e join employment_revisions r on r.company_id=e.company_id and r.employment_id=? and r.revision=0
            where e.company_id=? and not exists(select 1 from employment_revisions existing where existing.company_id=e.company_id and existing.employment_id=e.id)""",
                f.employee,
                f.company,
            )
        val actor = accountingActor(f)
        val review = reviewSchedule.execute(actor, f.type, YearMonth.of(2026, 10), 12)
        assertTrue(review is Result.Success, review.toString())
        val employees = (review as Result.Success).value.employeeIds.toSet()
        assertEquals(5000, employees.size)
        val started = System.nanoTime()
        val saved =
            scheduleAccrual.execute(
                actor,
                UUID.randomUUID(),
                scheduleCommand(f, periods = 12, employees = employees),
            )
        val elapsed = java.time.Duration.ofNanos(System.nanoTime() - started)
        assertTrue(saved is Result.Success, saved.toString())
        assertEquals(12, accountingRows(f, "background_jobs"))
        assertEquals(60000, accountingRows(f, "leave_batch_targets"))
        assertEquals(0, accountingRows(f, "leave_accrual_postings"))
        println(
            "Maximum accrual schedule admission: ${elapsed.toMillis()} ms for 12 periods and 5000 employees"
        )
    }

    @Test
    fun invalidSelectionsStalePoliciesAndForeignResourcesCannotCreateWork() {
        val at = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(1)
        val f = scheduledFixture(at = at)
        val other = scheduledFixture(at = at)
        val before = scheduleRows(f)
        for (input in
            listOf(
                scheduleCommand(f, periods = 0),
                scheduleCommand(f, periods = 13),
                scheduleCommand(f, first = "2199-12", periods = 2),
                scheduleCommand(f, employees = emptySet()),
            )) accountingCode(submitSchedule(f, input), 422, "invalid_leave_accrual_schedule")
        accountingCode(
            submitSchedule(f, scheduleCommand(f, version = 0)),
            409,
            "stale_policy_version",
        )
        accountingCode(
            submitSchedule(f, scheduleCommand(f, employees = setOf(other.employee))),
            422,
            "leave_batch_employee_unavailable",
        )
        accountingCode(
            submitSchedule(f, scheduleCommand(f).copy(typeId = other.type)),
            422,
            "leave_type_unavailable",
        )
        assertEquals(before, scheduleRows(f))
        val input =
            scheduleCommand(
                f,
                first =
                    YearMonth.from(at.atZone(ZoneId.of("Asia/Jakarta"))).minusMonths(1).toString(),
                periods = 1,
            )
        accountingBody(submitSchedule(f, input))
        accountingCode(
            get(
                other.admin,
                "/api/v1/companies/${other.company}/leave/accrual-schedules/${input.id}",
            ),
            404,
            "leave_accrual_schedule_not_found",
        )
        assertEquals(
            403,
            get(f.worker, "/api/v1/companies/${f.company}/leave/accrual-schedules").statusCode(),
        )
        assertEquals(
            403,
            get(
                    f.worker,
                    "/api/v1/companies/${f.company}/leave/accrual-schedules/review?typeId=${f.type}&firstPeriod=2026-10&periods=1",
                )
                .statusCode(),
        )
        accountingBody(accountingPolicy(f, version = 1, days = "2"))
        val lease =
            claimDocuments(JobKind.LEAVE_ACCRUAL).single { it.job.request.companyId == f.company }
        failure(stepBatch(f, lease), "leave_batch_policy_changed")
        assertEquals(0, accountingRows(f, "leave_accrual_postings"))
    }
}
