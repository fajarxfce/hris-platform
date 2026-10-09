package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollRunTransactionHttpTest : PayrollRunApiFixture() {
    @Test
    fun aBatchFailureUsesSanitizedDiagnosticsWithoutFrameworkSqlOrValues() {
        val f = calculationFixture(extraEmployees = 1)
        val framework =
            org.slf4j.LoggerFactory.getLogger(
                "org.springframework.boot.jooq.autoconfigure.ExceptionTranslatorExecuteListener"
            ) as ch.qos.logback.classic.Logger
        val boundary =
            org.slf4j.LoggerFactory.getLogger("dev.fajar.hris.database")
                as ch.qos.logback.classic.Logger
        val raw = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        val safe = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        raw.start()
        safe.start()
        framework.addAppender(raw)
        boundary.addAppender(safe)
        try {
            runProbe.invalidTarget = true
            payrollError(createRun(f), 409, "data_conflict")
            assertTrue(raw.list.isEmpty())
            val output = safe.list.joinToString { it.formattedMessage }
            assertTrue(output.contains("23514"))
            assertTrue(output.contains("PostgresPayrollRunDataSource"))
            assertFalse(output.contains("insert into"))
            assertFalse(output.contains(f.people.payroll.employee.toString()))
            assertTrue(safe.list.all { it.throwableProxy == null })
        } finally {
            framework.detachAppender(raw)
            boundary.detachAppender(safe)
            raw.stop()
            safe.stop()
            runProbe.clear()
        }
    }

    @Test
    fun aMissingTargetOrLateAuditFailureCannotLeaveAStartedRun() {
        val f = calculationFixture()
        val key = UUID.randomUUID()
        val body = runBody(f)
        val tables =
            listOf(
                "payroll_runs",
                "payroll_run_targets",
                "payroll_run_attempts",
                "background_jobs",
                "payroll_period_changes",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
        val before = tables.associateWith { count(f.people.payroll.company, it) }
        runProbe.omitTargets = true
        assertEquals(409, createRun(f, body, key).statusCode())
        assertEquals(before, tables.associateWith { count(f.people.payroll.company, it) })
        runProbe.clear()
        periodProbe.omitTransition = true
        assertEquals(409, createRun(f, body, key).statusCode())
        assertEquals(before, tables.associateWith { count(f.people.payroll.company, it) })
        periodProbe.clear()
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.calculation_started")
                throw DataIntegrityViolationException("Owned start failure")
        }
        assertEquals(409, createRun(f, body, key).statusCode())
        assertEquals(before, tables.associateWith { count(f.people.payroll.company, it) })
        payrollProbe.clear()
        payrollBody(createRun(f, body, key))
    }

    @Test
    fun resultsCountersCheckpointAndAuditCommitOrRollbackTogether() {
        val f = calculationFixture()
        val lease = beginRun(f)
        val tables = listOf("payroll_run_results", "audit_entries", "outbox_events")
        val before = tables.associateWith { count(f.people.payroll.company, it) }
        for (mode in 0..3) {
            when (mode) {
                0 -> runProbe.omitResult = true
                1 -> runProbe.omitCounter = true
                2 -> runProbe.afterResult = {
                        throw DataIntegrityViolationException("Owned result failure")
                    }
                3 -> payrollProbe.beforeJournal = {
                        if (it.action == "payroll.employee_calculated")
                            throw DataIntegrityViolationException("Owned employee audit failure")
                    }
            }
            assertTrue(stepRun(f, lease) is Result.Failed)
            assertEquals(before, tables.associateWith { count(f.people.payroll.company, it) })
            val view = runView(f, runId(lease))
            assertEquals(0, view["run"]["processed"].asInt())
            assertEquals(0, view["job"]["completedItems"].asInt())
            runProbe.clear()
            payrollProbe.clear()
        }
        assertEquals(Result.Success(JobStep(1, false)), stepRun(f, lease))
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.calculation_completed")
                throw DataIntegrityViolationException("Owned final audit failure")
        }
        assertTrue(stepRun(f, lease) is Result.Failed)
        val waiting = runView(f, runId(lease))
        assertEquals(1, waiting["run"]["processed"].asInt())
        assertEquals("PROCESSING", waiting["run"]["status"].asString())
        assertEquals(1, waiting["job"]["completedItems"].asInt())
        payrollProbe.clear()
        assertEquals(Result.Success(JobStep(2, true)), stepRun(f, lease))
    }

    @Test
    fun lateInterruptionRollsBackPendingResultAndReleasesTheThreadForReuse() {
        val f = calculationFixture()
        val lease = beginRun(f)
        runProbe.afterResult = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Owned pending cancellation")
        }
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<JobStep>> { stepRun(f, lease) }
            val failure =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertInstanceOf(InterruptedException::class.java, failure.cause)
            assertFalse(
                executor
                    .submit<Boolean> { Thread.currentThread().isInterrupted }
                    .get(5, TimeUnit.SECONDS)
            )
        }
        assertEquals(0, count(f.people.payroll.company, "payroll_run_results"))
        assertEquals(0, runView(f, runId(lease))["job"]["completedItems"].asInt())
        runProbe.clear()
        drainRun(f, lease)
    }

    @Test
    fun recoveryAndAbandonmentAuditFailureRetainsThePreviousStateAndOperationKey() {
        val f = calculationFixture()
        val first = beginRun(f)
        val id = runId(first)
        stopRun(f, first)
        val key = UUID.randomUUID()
        val before = count(f.people.payroll.company, "background_jobs")
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.calculation_resumed")
                throw DataIntegrityViolationException("Owned resume failure")
        }
        assertEquals(409, resumeRun(f, id, key = key).statusCode())
        assertEquals(before, count(f.people.payroll.company, "background_jobs"))
        assertEquals(1, count(f.people.payroll.company, "payroll_run_attempts"))
        payrollProbe.clear()
        payrollBody(resumeRun(f, id, key = key))
        val next = claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single()
        drainRun(f, next)
        val version = runView(f, id)["run"]["version"].asLong()
        val abandonKey = UUID.randomUUID()
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.calculation_abandoned")
                throw DataIntegrityViolationException("Owned abandon failure")
        }
        assertEquals(409, abandonRun(f, id, version, key = abandonKey).statusCode())
        assertEquals("CALCULATED", runView(f, id)["run"]["status"].asString())
        payrollProbe.clear()
        payrollBody(abandonRun(f, id, version, key = abandonKey))
    }

    @Test
    fun immutableRunEvidenceIsIsolatedByCompanyAndNotExposedToEmployeeAccounts() {
        val f = calculationFixture()
        val lease = beginRun(f)
        val id = runId(lease)
        drainRun(f, lease)
        val other = calculationFixture(prepare = false)
        for (table in
            listOf(
                "payroll_runs",
                "payroll_run_targets",
                "payroll_run_attempts",
                "payroll_run_results",
            )) {
            assertThrows(DataAccessException::class.java) {
                database().update("delete from $table where company_id=?", f.people.payroll.company)
            }
            val hidden =
                transactions.run(payrollActor(other.people.payroll, other.people.preparer)) {
                    safeDatabaseCall {
                        runtimeJdbc.queryForObject(
                            "select count(*) from $table where company_id=?",
                            Int::class.java,
                            f.people.payroll.company,
                        )!!
                    }
                }
            assertEquals(Result.Success(0), hidden)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update payroll_run_results set taxable_gross=1 where company_id=?",
                    f.people.payroll.company,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update payroll_runs set processed=0 where company_id=?",
                    f.people.payroll.company,
                )
        }
        payrollError(get(f.people.payroll.owner.client, runPath(f, id)), 403, "access_denied")
        payrollError(
            get(other.people.reviewer.client, runPath(other, id)),
            404,
            "payroll_run_not_found",
        )
        payrollError(
            get(
                f.people.reviewer.client,
                runPath(f, id) + "/employees/${other.people.payroll.employee}",
            ),
            404,
            "payroll_run_result_not_found",
        )
    }
}
