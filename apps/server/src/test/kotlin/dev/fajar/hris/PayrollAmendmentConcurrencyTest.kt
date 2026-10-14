package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentConcurrencyTest : PayrollAmendmentApiFixture() {
    @Test
    fun sharedReviewsRemainCoherentWhileFollowingMonthPublicationWaitsForThePayrollGuard() {
        val f = amendmentFixture()
        val second = followingMonth(f.publication.calculation, YearMonth.of(2026, 10))
        val calculation = beginTaxRun(second)
        drainRun(second, calculation)
        val run = runId(calculation)
        val review = UUID.fromString(payrollBody(submitReview(second, run))["id"].asString())
        payrollBody(decideReview(second, review))
        val finalizer = payrollMember(f.payroll.company, setOf("company.read", "payroll.finalize"))
        val publication = PublicationFixture(second, run, review, finalizer)
        val lease = beginFinalization(publication)
        val actor = payrollActor(f.payroll, f.maker)
        val before = getAmendmentPreparation.execute(actor, f.assessment)
        assertTrue(before is Result.Success, before.toString())
        val sources = (before as Result.Success).value.sources
        val versions = sources.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
        val remuneration =
            listOf(
                PayrollAmendmentRemuneration(
                    f.assessment,
                    sources
                        .first()
                        .facts
                        .compensation
                        .copy(
                            basicSalary =
                                sources.first().facts.compensation.basicSalary +
                                    BigDecimal("1000000")
                        ),
                )
            )
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)
        val armed = AtomicBoolean(true)
        amendmentProbe.afterRead = { company ->
            if (company == f.payroll.company && armed.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        payrollProbe.beforePolicyLock = { company, shared ->
            if (company == f.payroll.company && !shared) writerEntered.countDown()
        }
        Executors.newFixedThreadPool(3).use { pool ->
            val read =
                pool.submit<Result<PayrollAmendmentPreview>> {
                    previewAmendment.execute(actor, f.assessment, versions, remuneration)
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val concurrent =
                    pool
                        .submit<Result<PayrollAmendmentPreparation>> {
                            getAmendmentPreparation.execute(actor, f.assessment)
                        }
                        .get(3, TimeUnit.SECONDS)
                assertEquals(before, concurrent)
                val write = pool.submit<Result<*>> { stepFinalization(publication, lease) }
                assertTrue(writerEntered.await(5, TimeUnit.SECONDS))
                assertThrows(TimeoutException::class.java) { write.get(200, TimeUnit.MILLISECONDS) }
                release.countDown()
                val result = read.get(10, TimeUnit.SECONDS)
                assertTrue(result is Result.Success, result.toString())
                assertEquals(1, (result as Result.Success).value.calculations.size)
                val published = write.get(10, TimeUnit.SECONDS)
                assertTrue(published is Result.Success, published.toString())
            } finally {
                release.countDown()
                amendmentProbe.clear()
                payrollProbe.clear()
            }
        }
        val changed = getAmendmentPreparation.execute(actor, f.assessment)
        assertEquals(2, (changed as Result.Success).value.sources.size)
        val stale = previewAmendment.execute(actor, f.assessment, versions, remuneration)
        assertEquals("payroll_amendment_sources_changed", (stale as Result.Failed).failure.code)
    }
}
