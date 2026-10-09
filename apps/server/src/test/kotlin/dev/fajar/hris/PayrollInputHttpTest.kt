package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.jooq.JSONB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollInputHttpTest : PayrollPeriodApiFixture() {
    @Test
    fun verifiedInputSurvivesDraftReplacementAndHistoryListsRemainSmall() {
        val f = processingFixture()
        val source = closeWork(f)
        val periodId = UUID.fromString(payrollBody(periodCreate(f))["id"].asString())
        val key = UUID.randomUUID()
        val original = inputBody(source)
        val receipt = payrollBody(inputSave(f, source, original, key))
        val reviewKey = UUID.randomUUID()
        val review = payrollBody(inputVerify(f, key = reviewKey))
        val initial = payrollBody(get(f.reviewer.client, inputPath(f)))
        assertEquals("VERIFIED", initial["status"].asString())
        assertEquals(source.job.toString(), initial["workJobId"].asString())
        val members =
            payrollBody(get(f.reviewer.client, periodsPath(f) + "/$periodId"))["members"]["items"]
        assertEquals("VERIFIED", members[0]["inputStatus"].asString())
        payrollBody(periodCancel(f, periodId))
        payrollBody(periodCreate(f))
        assertEquals(
            "VERIFIED",
            payrollBody(get(f.reviewer.client, inputPath(f)))["status"].asString(),
        )
        payrollBody(
            inputSave(
                f,
                source,
                inputBody(source, 1, termChanges = mapOf("nonCashTaxable" to "500000")),
            )
        )
        assertEquals(
            "DRAFT",
            payrollBody(get(f.reviewer.client, inputPath(f)))["status"].asString(),
        )
        assertEquals(receipt, payrollBody(inputSave(f, source, original, key)))
        assertEquals(review, payrollBody(inputVerify(f, key = reviewKey)))
        val history = payrollBody(get(f.reviewer.client, inputPath(f) + "/history?limit=1"))
        assertFalse(history["items"][0].has("terms"))
        val old = payrollBody(get(f.reviewer.client, inputPath(f) + "?revision=1"))
        assertEquals("VERIFIED", old["status"].asString())
        assertEquals("0", old["terms"]["nonCashTaxable"].asString())
        val list =
            payrollBody(
                get(
                    f.reviewer.client,
                    "/api/v1/companies/${f.payroll.company}/payroll/inputs?month=2026-09",
                )
            )
        assertFalse(list["items"][0].has("terms"))
        assertEquals(1, list["items"].size())
        payrollBody(inputVerify(f, 2))
        assertEquals(4, count(f.payroll.company, "payroll_input_revisions"))
    }

    @Test
    fun inputsRequireAnExactClosedWorkforceSnapshotFromTheirEmployeeAndCompany() {
        val pending = processingFixture()
        val pendingSource = closeWork(pending, finish = false)
        assertFalse(workSource(pending)["closed"].asBoolean())
        payrollError(inputSave(pending, pendingSource), 409, "payroll_workforce_not_closed")
        val f = processingFixture()
        val source = closeWork(f)
        val other = processingFixture()
        val foreign = closeWork(other)
        val key = UUID.randomUUID()
        payrollError(
            inputSave(
                f,
                source,
                inputBody(source, changes = mapOf("workJobId" to foreign.job)),
                key,
            ),
            409,
            "stale_workforce_version",
        )
        payrollError(
            inputSave(
                f,
                source,
                inputBody(
                    source,
                    changes = mapOf("expectedWorkPeriodVersion" to source.version + 1),
                ),
                key,
            ),
            409,
            "stale_workforce_version",
        )
        payrollError(
            inputSave(other, foreign, employee = f.payroll.employee),
            404,
            "employee_not_found",
        )
        val late = employee(f.payroll.admin, f.payroll.adminCsrf, f.payroll.company)
        assertFalse(workSource(f, late)["includesEmployee"].asBoolean())
        payrollError(
            inputSave(f, source, employee = late),
            409,
            "payroll_workforce_employee_missing",
        )
        payrollBody(inputSave(f, source, key = key))
    }

    @Test
    fun inputVerificationExcludesHistoricalAuthorsAndCurrentBeneficiaries() {
        val f = processingFixture()
        val own =
            employee(
                f.payroll.admin,
                f.payroll.adminCsrf,
                f.payroll.company,
                account = f.preparer.account,
            )
        val reviewerOwn =
            employee(
                f.payroll.admin,
                f.payroll.adminCsrf,
                f.payroll.company,
                account = f.reviewer.account,
            )
        val source = closeWork(f)
        payrollError(inputSave(f, source, employee = own), 403, "self_payroll_input_change_denied")
        payrollBody(inputSave(f, source))
        payrollError(inputVerify(f, member = f.preparer), 403, "payroll_input_author_cannot_verify")
        val next =
            payrollMember(
                f.payroll.company,
                setOf("company.read", "payroll.calculate", "payroll.review"),
            )
        payrollBody(inputSave(f, source, inputBody(source, 0), member = next))
        payrollError(
            inputVerify(f, 1, member = f.preparer),
            403,
            "payroll_input_author_cannot_verify",
        )
        payrollBody(inputVerify(f, 1))
        payrollBody(inputSave(f, source, employee = reviewerOwn))
        payrollError(
            inputVerify(f, employee = reviewerOwn),
            403,
            "self_payroll_input_review_denied",
        )
        payrollError(get(f.payroll.owner.client, inputPath(f)), 403, "access_denied")
        payrollError(inputSave(f, source, member = f.payroll.operator), 403, "access_denied")
    }

    @Test
    fun invalidComponentsAndStaleEmploymentLeaveNoPartialOrUnverifiedChanges() {
        val f = processingFixture()
        val source = closeWork(f)
        val key = UUID.randomUUID()
        val day =
            mapOf(
                "workDate" to "2026-09-01",
                "portion" to "FULL",
                "disposition" to "UNPAID",
                "reference" to "Reviewed absence",
            )
        for (changes in
            listOf(
                mapOf("reviewReference" to ""),
                mapOf("nonCashTaxable" to "-1"),
                mapOf("scheduledMonthUnits" to "0.5"),
                mapOf("scheduledMonthUnits" to "22.1"),
                mapOf("dayResolutions" to listOf(day, day)),
                mapOf("dayResolutions" to listOf(day + mapOf("workDate" to "2026-10-01"))),
                mapOf(
                    "earnings" to
                        (1..41).map {
                            mapOf(
                                "code" to "A$it",
                                "name" to "Variable",
                                "amount" to "1",
                                "taxable" to true,
                            )
                        }
                ),
            )) payrollError(
            inputSave(f, source, inputBody(source, termChanges = changes), key),
            422,
            "invalid_payroll_input",
        )
        payrollError(
            inputSave(
                f,
                source,
                inputBody(source, termChanges = mapOf("nonCashTaxable" to "1e1000000")),
                key,
            ),
            422,
            "invalid_decimal_amount",
        )
        assertEquals(0, count(f.payroll.company, "payroll_inputs"))
        payrollBody(inputSave(f, source, key = key))
        assertEquals(
            200,
            revise(
                    f.payroll.admin,
                    f.payroll.adminCsrf,
                    f.payroll.company,
                    f.payroll.employee,
                    0,
                    terms("2026-11-01"),
                )
                .statusCode(),
        )
        payrollError(inputVerify(f), 409, "stale_employment_version")
        payrollBody(
            inputSave(
                f,
                source,
                inputBody(source, 0, changes = mapOf("expectedEmploymentVersion" to 1)),
            )
        )
        payrollBody(inputVerify(f, 1))
    }

    @Test
    fun competingEditsAndVerificationAdvanceOnlyOneInputRevision() {
        val f = processingFixture()
        val source = closeWork(f)
        val key = UUID.randomUUID()
        Executors.newFixedThreadPool(3).use { executor ->
            val receipts =
                (1..3)
                    .map {
                        executor.submit<java.net.http.HttpResponse<String>> {
                            inputSave(f, source, key = key)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, receipts.toSet().size)
        }
        Executors.newFixedThreadPool(2).use { executor ->
            val edit =
                executor.submit<java.net.http.HttpResponse<String>> {
                    inputSave(f, source, inputBody(source, 0))
                }
            val review = executor.submit<java.net.http.HttpResponse<String>> { inputVerify(f) }
            assertEquals(
                listOf(200, 409),
                listOf(edit, review).map { it.get(10, TimeUnit.SECONDS).statusCode() }.sorted(),
            )
        }
        assertEquals(2, count(f.payroll.company, "payroll_input_revisions"))
    }

    @Test
    fun missingEvidenceForgedVerificationAndJournalFailuresRollBackInputs() {
        val f = processingFixture()
        val source = closeWork(f)
        val company = f.payroll.company
        val key = UUID.randomUUID()
        val receipts = count(company, "operation_receipts")
        periodProbe.omitInputRevision = true
        assertEquals(409, inputSave(f, source, key = key).statusCode())
        periodProbe.clear()
        assertEquals(0, count(company, "payroll_inputs"))
        assertEquals(receipts, count(company, "operation_receipts"))
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.input_saved")
                throw DataIntegrityViolationException("Fixture journal failure")
        }
        assertEquals(409, inputSave(f, source, key = key).statusCode())
        payrollProbe.clear()
        assertEquals(0, count(company, "payroll_inputs"))
        payrollBody(inputSave(f, source, key = key))
        val reviewKey = UUID.randomUUID()
        periodProbe.beforeInputAppend = { row ->
            if (row.status == "VERIFIED")
                row.terms = JSONB.valueOf(row.terms.data().replace("1000000", "2000000"))
        }
        assertEquals(409, inputVerify(f, key = reviewKey).statusCode())
        periodProbe.clear()
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.input_verified")
                throw DataIntegrityViolationException("Fixture journal failure")
        }
        assertEquals(409, inputVerify(f, key = reviewKey).statusCode())
        payrollProbe.clear()
        assertEquals(1, count(company, "payroll_input_revisions"))
        payrollBody(inputVerify(f, key = reviewKey))
        for (statement in
            listOf(
                "delete from payroll_inputs where company_id=?",
                "update payroll_input_revisions set reason='changed' where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, company)
        }
    }
}
