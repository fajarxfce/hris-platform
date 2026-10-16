package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentAttendanceBoundaryTest : PayrollAmendmentAttendanceApiFixture() {
    @Test
    fun aCorrectionWhileCalculationWaitsRequiresAnExplicitNewSourceReview() {
        val f = amendmentFixture()
        val first = adjustedWork(f)
        val draft = attendanceDraft(f, first)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        amendmentProbe.beforeWorkLock = { company ->
            if (company == f.payroll.company) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { draftCommand(draft, "calculate", 0) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                adjustedWork(f, absence = true)
                release.countDown()
                payrollError(pending.get(10, TimeUnit.SECONDS), 409, "stale_workforce_version")
            } finally {
                release.countDown()
                amendmentProbe.clear()
            }
        }
        assertEquals("DRAFT", draftView(draft)["amendment"]["summary"]["status"].asString())
        assertEquals(0, count(f.payroll.company, "payroll_amendment_calculations"))
    }

    @Test
    fun callerCollectionsCannotChangeACommandAfterItsSourceWaitBegins() {
        val f = amendmentFixture()
        val draft = attendanceDraft(f, adjustedWork(f))
        val previous = draftTerms(draft)
        val references = previous.sourceVersions.toMutableList()
        val earnings =
            mutableListOf(
                PayrollVariableEarning("BONUS", "Reviewed bonus", BigDecimal("125000"), true)
            )
        val corrections =
            mutableListOf(
                previous.corrections
                    .single()
                    .copy(input = previous.corrections.single().input!!.copy(earnings = earnings))
            )
        val terms = PayrollAmendmentTerms(references, corrections)
        amendmentProbe.beforeWorkLock = { company ->
            if (company == f.payroll.company) {
                references.clear()
                corrections.clear()
                earnings.clear()
            }
        }
        val receipt =
            saveAmendment.execute(
                payrollActor(f.payroll, f.maker),
                UUID.randomUUID(),
                draft.id,
                f.assessment,
                0,
                terms,
                "Reviewed bonus with fixed attendance",
            )
        amendmentProbe.clear()
        assertTrue(receipt is Result.Success, receipt.toString())
        assertEquals(
            "125000",
            draftTerms(draft).corrections.single().input!!.earnings.single().amount.toPlainString(),
        )
        assertEquals(1, draftTerms(draft).sourceVersions.size)
    }

    @Test
    fun invalidMappingAndCancellationRollBackWithoutRecordingARuleFailureOrLeakingTheTransaction() {
        val f = amendmentFixture()
        val draft = attendanceDraft(f, adjustedWork(f))
        val before = draftCounts(f)
        val key = UUID.randomUUID()
        amendmentProbe.attendanceTransform = { _, rows ->
            rows.map { it.copy(month = it.month.plusMonths(1)) }
        }
        payrollError(draftCommand(draft, "calculate", 0, key), 500, "database_failure")
        amendmentProbe.clear()
        assertEquals(before, draftCounts(f))
        amendmentProbe.afterAttendanceRead = {
            Thread.currentThread().interrupt()
            throw InterruptedException("Fixture interruption")
        }
        val actor = payrollActor(f.payroll, f.maker)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    calculateAmendment.execute(actor, key, draft.id, 0, "Reviewed remuneration")
                }
            try {
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertInstanceOf(InterruptedException::class.java, failure.cause)
            } finally {
                amendmentProbe.clear()
            }
            assertEquals(before, draftCounts(f))
            val recovered =
                pool
                    .submit<Result<MutationReceipt>> {
                        assertFalse(Thread.currentThread().isInterrupted)
                        calculateAmendment.execute(actor, key, draft.id, 0, "Reviewed remuneration")
                    }
                    .get(10, TimeUnit.SECONDS)
            assertTrue(recovered is Result.Success, recovered.toString())
        }
    }

    @Test
    fun recentAssuranceRemainsRequiredAfterAcquiringTheSelectedWorkSnapshot() {
        val f = amendmentFixture()
        val draft = attendanceDraft(f, adjustedWork(f))
        val security = IdentitySecurityPolicy()
        val actor =
            payrollActor(f.payroll, f.maker)
                .copy(
                    authenticatedAt =
                        clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
                )
        val before = draftCounts(f)
        val key = UUID.randomUUID()
        amendmentProbe.afterAttendanceRead = { clock.set(clock.instant().plusSeconds(2)) }
        val rejected = calculateAmendment.execute(actor, key, draft.id, 0, "Reviewed remuneration")
        amendmentProbe.clear()
        assertEquals("recent_authentication_required", (rejected as Result.Failed).failure.code)
        assertEquals(before, draftCounts(f))
        val retried =
            calculateAmendment.execute(
                actor.copy(authenticatedAt = clock.instant()),
                key,
                draft.id,
                0,
                "Reviewed remuneration",
            )
        assertTrue(retried is Result.Success, retried.toString())
    }
}
