package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class LeaveAccrualScheduleTransactionTest : LeaveAccrualScheduleApiFixture() {
    @ParameterizedTest
    @ValueSource(strings = ["missing-period", "early-period", "late-journal"])
    fun incompletePlansAndLateFailuresRollBackAllJobsBatchesAuditAndReceipt(fault: String) {
        val f = scheduledFixture()
        val input = scheduleCommand(f)
        val operation = UUID.randomUUID()
        val before = scheduleRows(f)
        when (fault) {
            "missing-period" -> scheduleProbe.omitPeriod = true
            "early-period" -> scheduleProbe.earlyPeriod = true
            "late-journal" -> accountingProbe.beforeJournal = {
                    throw DataIntegrityViolationException("Fixture schedule journal failure")
                }
        }
        try {
            assertEquals(409, submitSchedule(f, input, operation).statusCode())
        } finally {
            scheduleProbe.clear()
            accountingProbe.clear()
        }
        assertEquals(before, scheduleRows(f))
        accountingBody(submitSchedule(f, input, operation))
        assertEquals(3, accountingRows(f, "background_jobs"))
        assertEquals(1, accountingRows(f, "leave_accrual_schedules"))
    }

    @Test
    fun interruptionAfterCreatingAllPeriodsRollsBackAndPropagatesCancellation() {
        val f = scheduledFixture()
        val input = scheduleCommand(f)
        val actor = accountingActor(f)
        val before = scheduleRows(f)
        accountingProbe.beforeJournal = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Fixture schedule cancellation")
        }
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<MutationReceipt>> {
                        scheduleAccrual.execute(actor, UUID.randomUUID(), input)
                    }
                val error =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertTrue(error.cause is InterruptedException)
            }
        } finally {
            accountingProbe.clear()
        }
        assertEquals(before, scheduleRows(f))
        accountingBody(submitSchedule(f, input))
    }

    @Test
    fun scheduleEvidenceIsImmutableScopedAndCannotAcceptExtraPeriods() {
        val f = scheduledFixture()
        val other = scheduledFixture()
        val input = scheduleCommand(f, periods = 1)
        accountingBody(submitSchedule(f, input))
        for (table in listOf("leave_accrual_schedules", "leave_accrual_schedule_entries")) {
            assertThrows(DataAccessException::class.java) {
                database().update("delete from $table where company_id=?", f.company)
            }
            val count =
                transactions.run(accountingActor(other)) {
                    safeDatabaseCall {
                        runtimeJdbc.queryForObject(
                            "select count(*) from $table where company_id=?",
                            Int::class.java,
                            f.company,
                        )
                    }
                }
            assertEquals(Result.Success(0), count)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_accrual_schedules set reason='rewrite' where company_id=?",
                    f.company,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_accrual_schedule_entries set scheduled_for=scheduled_for+interval '1 day' where company_id=?",
                    f.company,
                )
        }
        val unrelated = UUID.randomUUID()
        accountingBody(createBatch(f, batchBody(f, id = unrelated, employees = setOf(f.employee))))
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    """insert into leave_accrual_schedule_entries(company_id,schedule_id,ordinal,batch_id,initial_job_id,scheduled_for)
                select company_id,?,2,id,job_id,created_at from leave_batches where company_id=? and id=?""",
                    input.id,
                    f.company,
                    unrelated,
                )
        }
        assertEquals(1, accountingRows(f, "leave_accrual_schedule_entries"))
    }
}
