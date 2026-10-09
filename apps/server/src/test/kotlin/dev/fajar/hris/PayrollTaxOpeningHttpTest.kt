package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.jooq.JSONB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class PayrollTaxOpeningHttpTest : PayrollTaxOpeningApiFixture() {
    @Test
    fun draftVerificationAndReplacementRetainExactImmutableHistoryAndReplay() {
        val f = openingFixture()
        val key = UUID.randomUUID()
        val original = openingBody()
        val receipt = payrollBody(opening(f, original, key))
        val path = openingPath(f)
        assertEquals("DRAFT", payrollBody(get(f.reviewer.client, path))["status"].asString())
        val verifyKey = UUID.randomUUID()
        val verified = payrollBody(verify(f, key = verifyKey))
        assertEquals(verified, payrollBody(verify(f, key = verifyKey)))
        val current = payrollBody(get(f.reviewer.client, path))
        assertEquals("VERIFIED", current["status"].asString())
        assertEquals(f.reviewer.account.toString(), current["verifiedBy"].asString())
        payrollBody(
            opening(f, openingBody(1, historyChanges = mapOf("taxableGross" to "61000000")))
        )
        val replaced = payrollBody(get(f.reviewer.client, path))
        assertEquals("DRAFT", replaced["status"].asString())
        assertTrue(replaced["verifiedBy"].isNull)
        assertEquals(receipt, payrollBody(opening(f, original, key)))
        assertEquals(verified, payrollBody(verify(f, key = verifyKey)))
        val first = payrollBody(get(f.reviewer.client, "$path/history?limit=1"))
        assertEquals(1, first["items"].size())
        val second =
            payrollBody(
                get(
                    f.reviewer.client,
                    "$path/history?limit=1&after=${first["nextCursor"].asString()}",
                )
            )
        assertEquals("VERIFIED", second["items"][0]["status"].asString())
        assertEquals("60000000", second["items"][0]["terms"]["history"]["taxableGross"].asString())
        payrollBody(verify(f, 2))
        assertEquals(4, count(f.payroll.company, "payroll_tax_opening_revisions"))
    }

    @Test
    fun verificationExcludesEveryHistoricalPreparerAndTheCurrentBeneficiary() {
        val f = openingFixture()
        payrollBody(opening(f))
        payrollError(
            verify(f, member = f.preparer),
            403,
            "payroll_tax_opening_author_cannot_verify",
        )
        val other =
            payrollMember(
                f.payroll.company,
                setOf("company.read", "payroll.calculate", "payroll.review"),
            )
        payrollBody(opening(f, openingBody(0), member = other))
        payrollError(
            verify(f, 1, member = f.preparer),
            403,
            "payroll_tax_opening_author_cannot_verify",
        )
        payrollError(verify(f, 1, member = other), 403, "payroll_tax_opening_author_cannot_verify")
        payrollBody(verify(f, 1))
        val own =
            employee(
                f.payroll.admin,
                f.payroll.adminCsrf,
                f.payroll.company,
                account = f.preparer.account,
            )
        payrollError(opening(f, employee = own), 403, "self_payroll_input_change_denied")
        val reviewOwn =
            employee(
                f.payroll.admin,
                f.payroll.adminCsrf,
                f.payroll.company,
                account = f.reviewer.account,
            )
        payrollBody(opening(f, employee = reviewOwn))
        payrollError(verify(f, employee = reviewOwn), 403, "self_payroll_input_review_denied")
        for (browser in listOf(f.payroll.admin, f.payroll.owner.client)) payrollError(
            get(browser, openingPath(f)),
            403,
            "access_denied",
        )
        payrollError(opening(f, member = f.payroll.operator), 403, "access_denied")
    }

    @Test
    fun inputBoundsFutureFactsAndEmploymentVersionsLeaveRejectedOperationsReusable() {
        val f = openingFixture()
        val key = UUID.randomUUID()
        for (body in
            listOf(
                openingBody(termChanges = mapOf("throughMonth" to 0)),
                openingBody(termChanges = mapOf("throughMonth" to 12)),
                openingBody(termChanges = mapOf("reference" to "")),
                openingBody(historyChanges = mapOf("employmentMonths" to 7)),
                openingBody(historyChanges = mapOf("retirementContributions" to "61000000")),
                openingBody(historyChanges = mapOf("withheld" to "-1")),
                openingBody(historyChanges = mapOf("taxableGross" to "600000000001")),
                openingBody(
                    termChanges = mapOf("residency" to "NON_RESIDENT"),
                    historyChanges = mapOf("previousEmployerNet" to "1000000"),
                ),
            )) payrollError(opening(f, body, key), 422, "invalid_payroll_tax_opening")
        payrollError(
            opening(f, openingBody(historyChanges = mapOf("taxableGross" to "1e1000000")), key),
            422,
            "invalid_decimal_amount",
        )
        payrollError(
            opening(f, openingBody(termChanges = mapOf("throughMonth" to 11)), key),
            422,
            "payroll_tax_opening_future",
        )
        assertEquals(0, count(f.payroll.company, "payroll_tax_openings"))
        payrollBody(opening(f, key = key))
        payrollError(opening(f, openingBody(0), key), 409, "operation_payload_mismatch")
        assertEquals(
            200,
            revise(
                    f.payroll.admin,
                    f.payroll.adminCsrf,
                    f.payroll.company,
                    f.payroll.employee,
                    0,
                    terms("2026-07-01"),
                )
                .statusCode(),
        )
        payrollError(opening(f, openingBody(0)), 409, "stale_employment_version")
        payrollBody(opening(f, openingBody(0, changes = mapOf("expectedEmploymentVersion" to 1))))
    }

    @Test
    fun competingCreatesAndConcurrentEditingVerificationProduceOnlyOneRevision() {
        val f = openingFixture()
        val key = UUID.randomUUID()
        Executors.newFixedThreadPool(4).use { executor ->
            val replies =
                (1..4)
                    .map {
                        executor.submit<java.net.http.HttpResponse<String>> {
                            opening(f, key = key)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, replies.toSet().size)
        }
        assertEquals(1, count(f.payroll.company, "payroll_tax_opening_revisions"))
        Executors.newFixedThreadPool(2).use { executor ->
            val start = CountDownLatch(1)
            val edit =
                executor.submit<java.net.http.HttpResponse<String>> {
                    check(start.await(5, TimeUnit.SECONDS))
                    opening(f, openingBody(0, historyChanges = mapOf("taxableGross" to "62000000")))
                }
            val review =
                executor.submit<java.net.http.HttpResponse<String>> {
                    check(start.await(5, TimeUnit.SECONDS))
                    verify(f)
                }
            start.countDown()
            assertEquals(
                listOf(200, 409),
                listOf(edit, review).map { it.get(10, TimeUnit.SECONDS).statusCode() }.sorted(),
            )
        }
        assertEquals(2, count(f.payroll.company, "payroll_tax_opening_revisions"))
        val fresh = openingFixture()
        Executors.newFixedThreadPool(2).use { executor ->
            assertEquals(
                listOf(200, 409),
                (1..2)
                    .map { executor.submit<java.net.http.HttpResponse<String>> { opening(fresh) } }
                    .map { it.get(10, TimeUnit.SECONDS).statusCode() }
                    .sorted(),
            )
        }
        assertEquals(1, count(fresh.payroll.company, "payroll_tax_openings"))
    }

    @Test
    fun missingHistoryForgedVerificationAndAuditFailuresRollBackTheEntireCommand() {
        val f = openingFixture()
        val key = UUID.randomUUID()
        val company = f.payroll.company
        val receipts = count(company, "operation_receipts")
        val audit = count(company, "audit_entries")
        val outbox = count(company, "outbox_events")
        openingProbe.omitRevision = true
        assertEquals(409, opening(f, key = key).statusCode())
        openingProbe.clear()
        assertEquals(0, count(company, "payroll_tax_openings"))
        assertEquals(receipts, count(company, "operation_receipts"))
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.tax_opening_saved")
                throw DataIntegrityViolationException("Private fixture failure")
        }
        val failed = opening(f, key = key)
        assertEquals(409, failed.statusCode())
        assertFalse(failed.body().contains("Private fixture failure"))
        assertEquals(0, count(company, "payroll_tax_openings"))
        assertEquals(audit, count(company, "audit_entries"))
        assertEquals(outbox, count(company, "outbox_events"))
        payrollProbe.clear()
        payrollBody(opening(f, key = key))
        val reviewKey = UUID.randomUUID()
        val before = count(company, "operation_receipts")
        openingProbe.beforeAppend = { row ->
            if (row.status == "VERIFIED")
                row.terms = JSONB.valueOf(row.terms.data().replace("60000000", "61000000"))
        }
        assertEquals(409, verify(f, key = reviewKey).statusCode())
        openingProbe.clear()
        assertEquals(
            "DRAFT",
            payrollBody(get(f.reviewer.client, openingPath(f)))["status"].asString(),
        )
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.tax_opening_verified")
                throw DataIntegrityViolationException("Fixture audit rejection")
        }
        assertEquals(409, verify(f, key = reviewKey).statusCode())
        payrollProbe.clear()
        assertEquals(1, count(company, "payroll_tax_opening_revisions"))
        assertEquals(before, count(company, "operation_receipts"))
        payrollBody(verify(f, key = reviewKey))
    }

    @Test
    fun everyPageIsScopedAndHeaderAndEvidenceHistoryCannotBeRewritten() {
        val f = openingFixture()
        payrollBody(opening(f))
        val second = employee(f.payroll.admin, f.payroll.adminCsrf, f.payroll.company)
        payrollBody(opening(f, employee = second))
        payrollBody(verify(f))
        val path = "/api/v1/companies/${f.payroll.company}/payroll/tax-openings?year=2026"
        val first = payrollBody(get(f.reviewer.client, "$path&limit=1"))
        val next =
            payrollBody(
                get(f.reviewer.client, "$path&limit=1&after=${first["nextCursor"].asString()}")
            )
        assertEquals(1, next["items"].size())
        assertNotEquals(first["items"][0]["id"], next["items"][0]["id"])
        assertEquals(
            1,
            payrollBody(get(f.reviewer.client, "$path&status=VERIFIED"))["items"].size(),
        )
        payrollError(get(f.reviewer.client, "$path&limit=201"), 422, "invalid_page")
        for (sql in
            listOf(
                "delete from payroll_tax_openings where company_id=?",
                "update payroll_tax_openings set version=version+2 where company_id=?",
                "delete from payroll_tax_opening_revisions where company_id=?",
                "update payroll_tax_opening_revisions set reason='changed' where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(sql, f.payroll.company)
        }
        val other = openingFixture()
        payrollError(
            get(other.reviewer.client, openingPath(other, f.payroll.employee)),
            404,
            "payroll_tax_opening_not_found",
        )
        payrollError(opening(other, employee = f.payroll.employee), 404, "employee_not_found")
        val result =
            transactions.run(payrollActor(other.payroll, other.preparer)) {
                safeDatabaseCall {
                    runtimeJdbc.queryForObject(
                        "select count(*) from payroll_tax_opening_revisions where company_id=?",
                        Int::class.java,
                        f.payroll.company,
                    )!!
                }
            }
        assertEquals(Result.Success(0), result)
    }
}
