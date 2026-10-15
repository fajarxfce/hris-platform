package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobKind
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LeaveBatchReviewHttpTest : LeaveBatchApiFixture() {
    @Test
    fun companyReviewUsesEffectivePolicyAndCompanyDateWithoutCreatingWork() {
        val f = accountingFixture(at = Instant.parse("2026-09-30T18:00:00Z"))
        val review = accountingBody(get(f.admin, reviewPath(f)))
        assertEquals("2026-10-01", review["companyDate"].asString())
        assertEquals("Asia/Jakarta", review["timezone"].asString())
        assertEquals("2026-09", review["period"].asString())
        assertEquals("ACCRUAL", review["kind"].asString())
        assertEquals(f.type.toString(), review["type"]["id"].asString())
        assertEquals(1, review["type"]["version"].asLong())
        assertEquals(1, review["type"]["appliedRevision"].asLong())
        assertEquals(2, review["totalEmployees"].asInt())
        assertEquals(
            setOf(f.employee.toString(), f.manager.toString()),
            review["employeeIds"].iterator().asSequence().map { it.asString() }.toSet(),
        )
        assertEquals(0, accountingRows(f, "leave_batches"))
        assertEquals(0, accountingRows(f, "background_jobs"))
        assertEquals(0, accountingRows(f, "leave_ledger"))
    }

    @Test
    fun historicalPeriodRetainsItsAppliedRevisionWhenThePolicyHeadHasAdvanced() {
        val f = accountingFixture()
        accountingBody(accountingPolicy(f, version = 1, days = "2", from = "2027-01-01"))
        val review = accountingBody(get(f.admin, reviewPath(f)))
        assertEquals(2, review["type"]["version"].asLong())
        assertEquals(1, review["type"]["appliedRevision"].asLong())
        assertEquals("1", review["type"]["accrual"]["daysPerPeriod"].asString())
    }

    @Test
    fun discoveryIsBoundedAndRequiresTheChosenOperationWithoutPolicyAdministration() {
        val f = accountingFixture()
        val path = "/api/v1/companies/${f.company}/leave/batches/types?kind=ACCRUAL"
        accountingCode(get(f.worker, path), 403, "access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.accrual.post')",
                f.company,
                f.account,
            )
        val page = accountingBody(get(f.worker, path))
        assertEquals(1, page["items"].size())
        assertEquals(f.type.toString(), page["items"][0]["id"].asString())
        assertFalse(page["items"][0].has("reason"))
        accountingCode(get(f.worker, "$path&limit=51"), 422, "invalid_page")
        accountingCode(get(f.worker, "$path&after=invalid"), 422, "invalid_page")
        accountingCode(get(f.worker, path.replace("ACCRUAL", "YEAR_CLOSE")), 403, "access_denied")
    }

    @Test
    fun reviewRequiresTheSpecificPostingGrantWithoutRequiringPeopleOrPolicyManagement() {
        val f = accountingFixture()
        accountingCode(get(f.worker, reviewPath(f)), 403, "access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.accrual.post')",
                f.company,
                f.account,
            )
        accountingBody(get(f.worker, reviewPath(f)))
        accountingCode(
            get(f.worker, reviewPath(f, kind = "YEAR_CLOSE", period = "2025-12")),
            403,
            "access_denied",
        )
        accountingCode(
            get(f.worker, "/api/v1/companies/${f.company}/leave/policies/${f.type}"),
            403,
            "access_denied",
        )
        accountingCode(
            get(f.worker, "/api/v1/companies/${f.company}/employees/${f.manager}?asOf=2026-10-01"),
            404,
            "employee_not_found",
        )
    }

    @Test
    fun reviewRejectsForeignTypesAndAnAlreadyActiveBatch() {
        val f = accountingFixture()
        val foreign = accountingFixture()
        accountingCode(
            get(f.admin, reviewPath(f, type = foreign.type)),
            422,
            "leave_type_unavailable",
        )
        accountingBody(createBatch(f))
        accountingCode(get(f.admin, reviewPath(f)), 409, "leave_batch_active")
    }

    @ParameterizedTest
    @CsvSource(
        "ACCRUAL,2026-10,leave_accrual_not_due",
        "YEAR_CLOSE,2026-12,leave_year_not_ended",
        "YEAR_CLOSE,2025-11,invalid_leave_batch",
        "ACCRUAL,2200-01,invalid_leave_batch",
    )
    fun reviewUsesTheSamePeriodRulesAsSubmission(kind: String, period: String, code: String) {
        val f = accountingFixture()
        accountingCode(get(f.admin, reviewPath(f, kind, period)), 422, code)
    }

    @Test
    fun yearClosingCanReviewTheRetainedPolicyForAnEndedYear() {
        val f = accountingFixture(at = Instant.parse("2027-01-02T00:00:00Z"))
        val review =
            accountingBody(get(f.admin, reviewPath(f, kind = "YEAR_CLOSE", period = "2026-12")))
        assertEquals("YEAR_CLOSE", review["kind"].asString())
        assertEquals("2", review["type"]["accrual"]["carryLimitDays"].asString())
        assertEquals(2, review["totalEmployees"].asInt())
        assertEquals(0, accountingRows(f, "leave_batches"))
    }

    @Test
    fun outcomeLabelsAreBoundToTheReturnedPageAndDoNotRequirePeopleAdministration() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        drainBatch(f, lease)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.read')",
                f.company,
                f.account,
            )
        val first = batchView(f, batchId(lease), "?limit=1", f.worker)
        val references = first["employees"]
        assertEquals(1, references.size())
        assertEquals(
            first["results"]["items"][0]["employeeId"].asString(),
            references[0]["id"].asString(),
        )
        assertEquals("Example Employee", references[0]["name"].asString())
        assertTrue(references[0]["number"].asString().startsWith("E"))
        assertEquals(setOf("id", "number", "name"), references[0].propertyNames().toSet())
        assertEquals(0, first["availableActions"].size())
        val cursor = first["results"]["nextCursor"].asString()
        val second = batchView(f, batchId(lease), "?limit=1&after=$cursor", f.worker)
        assertNotEquals(references[0]["id"].asString(), second["employees"][0]["id"].asString())
        assertEquals(1, second["employees"].size())
    }

    @Test
    fun onlyTheOriginalAuthorCanResumeAnUnchangedStoppedBatch() {
        val f = accountingFixture()
        val lease = beginBatch(f)
        cancelBatch(f, lease)
        val stopped = batchView(f, batchId(lease))
        assertEquals(
            listOf("RESUME"),
            stopped["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        for (permission in listOf("leave.read", "leave.accrual.post")) {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    f.account,
                    permission,
                )
        }
        assertEquals(0, batchView(f, batchId(lease), client = f.worker)["availableActions"].size())
        accountingBody(accountingPolicy(f, version = 1, days = "2"))
        assertEquals(0, batchView(f, batchId(lease))["availableActions"].size())
    }

    @Test
    fun completedBatchRetainsMixedEmployeeOutcomesWithoutOfferingRecovery() {
        val f = accountingFixture(at = Instant.parse("2027-01-02T00:00:00Z"))
        val lease = beginBatch(f, kind = JobKind.LEAVE_YEAR_CLOSE)
        drainBatch(f, lease)
        val detail = batchView(f, batchId(lease))
        assertEquals("COMPLETED", detail["batch"]["status"].asString())
        assertEquals("SUCCEEDED", detail["job"]["status"].asString())
        assertEquals(2, detail["counts"]["completed"].asInt())
        assertEquals(0, detail["availableActions"].size())
    }

    private fun reviewPath(
        f: LeaveFixture,
        kind: String = "ACCRUAL",
        period: String = "2026-09",
        type: UUID = f.type,
    ): String =
        "/api/v1/companies/${f.company}/leave/batches/review?kind=$kind&period=$period&typeId=$type"
}
