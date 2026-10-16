package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.net.http.HttpResponse
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class PayrollAttendanceInputHttpTest : PayrollAttendanceSourceApiFixture() {
    @Test
    fun inputSelectsAnExactCurrentRevisionAndRetainsItAfterAnotherCorrection() {
        val f = processingFixture()
        val work = closeWork(f)
        val original = workSource(f)
        assertTrue(original["adjustment"].isNull)
        val first = lateAttendance(f)
        val source = workSource(f)
        assertEquals(original["jobId"], source["jobId"])
        assertEquals(original["version"], source["version"])
        assertEquals(first.toString(), source["adjustment"]["id"].asString())
        assertEquals(1, source["adjustment"]["revision"].asLong())
        assertEquals("2026-09-01", source["adjustment"]["workDate"].asString())
        val key = UUID.randomUUID()
        payrollError(inputSave(f, work, key = key), 409, "stale_workforce_version")
        val selected = inputBody(work, changes = mapOf("workAdjustmentId" to first))
        val saved = payrollBody(inputSave(f, work, selected, key))
        val verificationKey = UUID.randomUUID()
        val verified = payrollBody(inputVerify(f, key = verificationKey))
        val second = lateAttendance(f, absence = true)
        assertNotEquals(first, second)
        val view = payrollBody(get(f.reviewer.client, inputPath(f) + "/review"))
        assertEquals(first.toString(), view["current"]["workAdjustmentId"].asString())
        assertEquals(second.toString(), view["source"]["adjustment"]["id"].asString())
        assertEquals(0, view["availableActions"].size())
        assertEquals(saved, payrollBody(inputSave(f, work, selected, key)))
        assertEquals(verified, payrollBody(inputVerify(f, key = verificationKey)))
        payrollError(
            inputSave(f, work, inputBody(work, changes = mapOf("workAdjustmentId" to second)), key),
            409,
            "operation_payload_mismatch",
        )
        payrollBody(
            inputSave(f, work, inputBody(work, 1, changes = mapOf("workAdjustmentId" to second)))
        )
        payrollBody(inputVerify(f, 2))
        val historical = payrollBody(get(f.reviewer.client, inputPath(f) + "?revision=1"))
        assertEquals(first.toString(), historical["workAdjustmentId"].asString())
        assertEquals("VERIFIED", historical["status"].asString())
        assertEquals(
            second.toString(),
            payrollBody(get(f.reviewer.client, inputPath(f)))["workAdjustmentId"].asString(),
        )
    }

    @Test
    fun anotherEmployeesOrCompanysAdjustmentCannotBeSelected() {
        val f = processingFixture()
        val otherEmployee = employee(f.payroll.admin, f.payroll.adminCsrf, f.payroll.company)
        val work = closeWork(f)
        val current = lateAttendance(f)
        val sibling = lateAttendance(f, employee = otherEmployee)
        val other = processingFixture()
        closeWork(other)
        val foreign = lateAttendance(other)
        for (id in listOf(sibling, foreign, UUID.randomUUID())) {
            payrollError(
                inputSave(f, work, inputBody(work, changes = mapOf("workAdjustmentId" to id))),
                409,
                "stale_workforce_version",
            )
        }
        assertEquals(0, count(f.payroll.company, "payroll_inputs"))
        payrollBody(
            inputSave(f, work, inputBody(work, changes = mapOf("workAdjustmentId" to current)))
        )
        payrollError(inputVerify(f, member = f.preparer), 403, "payroll_input_author_cannot_verify")
        payrollBody(inputVerify(f))
        assertEquals(403, get(other.preparer.client, inputPath(f) + "/review").statusCode())
    }

    @Test
    fun verificationRechecksAttendanceAfterWaitingForItsSource() {
        val f = processingFixture()
        val work = closeWork(f)
        val first = lateAttendance(f)
        payrollBody(
            inputSave(f, work, inputBody(work, changes = mapOf("workAdjustmentId" to first)))
        )
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        periodProbe.beforeWorkSourceRead = { company ->
            if (company == f.payroll.company) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { inputVerify(f) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val next = lateAttendance(f, absence = true)
                release.countDown()
                payrollError(pending.get(10, TimeUnit.SECONDS), 409, "stale_workforce_version")
                periodProbe.clear()
                val review = payrollBody(get(f.reviewer.client, inputPath(f) + "/review"))
                assertEquals(next.toString(), review["source"]["adjustment"]["id"].asString())
                assertEquals(0, review["availableActions"].size())
                assertEquals("DRAFT", review["current"]["status"].asString())
            } finally {
                release.countDown()
                periodProbe.clear()
            }
        }
    }

    @Test
    fun forgedEvidenceAndLateJournalFailuresRollBackTheInputAndItsReceipt() {
        val f = processingFixture()
        val work = closeWork(f)
        val first = lateAttendance(f)
        val body = inputBody(work, changes = mapOf("workAdjustmentId" to first))
        val key = UUID.randomUUID()
        val before = inputState(f)
        periodProbe.beforeInputAppend = { it.workAdjustmentId = null }
        assertEquals(409, inputSave(f, work, body, key).statusCode())
        periodProbe.clear()
        assertEquals(before, inputState(f))
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.input_saved")
                throw DataIntegrityViolationException("Fixture failure")
        }
        assertEquals(409, inputSave(f, work, body, key).statusCode())
        payrollProbe.clear()
        assertEquals(before, inputState(f))
        payrollBody(inputSave(f, work, body, key))
        val draft = inputState(f)
        periodProbe.beforeInputAppend = { if (it.status == "VERIFIED") it.workAdjustmentId = null }
        assertEquals(409, inputVerify(f).statusCode())
        periodProbe.clear()
        assertEquals(draft, inputState(f))
        payrollBody(inputVerify(f))
    }

    @Test
    fun termsAreDetachedBeforeSourceWaitsAndCancellationReleasesTheTransaction() {
        val f = processingFixture()
        val work = closeWork(f)
        val adjustment = lateAttendance(f)
        val earnings =
            mutableListOf(
                PayrollVariableEarning("BONUS", "Reviewed bonus", BigDecimal("125000"), true)
            )
        val terms =
            PayrollInputTerms(
                earnings,
                emptyList(),
                BigDecimal.ZERO,
                null,
                emptyList(),
                null,
                "Reviewed input",
            )
        val key = UUID.randomUUID()
        periodProbe.beforeWorkSourceRead = { if (it == f.payroll.company) earnings.clear() }
        val saved =
            saveInput.execute(
                payrollActor(f.payroll, f.preparer),
                key,
                f.payroll.employee,
                YearMonth.of(2026, 9),
                work.job,
                work.version,
                0,
                terms,
                null,
                "Prepare input",
                adjustment,
            )
        assertTrue(saved is Result.Success, saved.toString())
        periodProbe.clear()
        assertEquals(0, earnings.size)
        assertEquals(
            "125000",
            payrollBody(get(f.reviewer.client, inputPath(f)))["terms"]["earnings"][0]["amount"]
                .asString(),
        )

        val before = inputState(f)
        periodProbe.beforeWorkSourceRead = {
            if (it == f.payroll.company) throw InterruptedException("Cancelled source read")
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    saveInput.execute(
                        payrollActor(f.payroll, f.preparer),
                        UUID.randomUUID(),
                        f.payroll.employee,
                        YearMonth.of(2026, 9),
                        work.job,
                        work.version,
                        0,
                        terms,
                        0,
                        "Replace input",
                        adjustment,
                    )
                }
            assertInstanceOf(
                InterruptedException::class.java,
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
                    .cause,
            )
        }
        periodProbe.clear()
        assertEquals(before, inputState(f))
        payrollBody(
            inputSave(
                f,
                work,
                inputBody(work, 0, changes = mapOf("workAdjustmentId" to adjustment)),
            )
        )
    }
}
