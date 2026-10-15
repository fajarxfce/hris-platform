package dev.fajar.hris

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.TransientDataAccessResourceException

class PayrollAmendmentDiscoveryHttpTest : PayrollAmendmentApiFixture() {
    @Test
    fun discoveryUsesItsOwnAuthorityAndOnlyReturnsRetainedEmployeeMonthReferences() {
        val f = amendmentFixture()
        val base = "/api/v1/companies/${f.payroll.company}/payroll/amendment-sources?year=2026"
        val tables =
            listOf("operation_receipts", "payroll_amendments", "payroll_amendment_calculations")
        val before = tables.associateWith { count(f.payroll.company, it) }
        val response = get(f.maker.client, base)
        assertTrue(response.headers().allValues("Cache-Control").any { it.contains("no-store") })
        val page = payrollBody(response)
        assertTrue(page["nextCursor"].isNull)
        val candidate = page["items"].single()
        assertEquals(
            setOf(
                "assessmentId",
                "employeeId",
                "employeeNumber",
                "employeeName",
                "taxMonth",
                "revision",
                "publishedAt",
            ),
            candidate.propertyNames().toSet(),
        )
        assertEquals(f.assessment.toString(), candidate["assessmentId"].asString())
        assertEquals("2026-09", candidate["taxMonth"].asString())
        assertEquals(0, candidate["revision"].asLong())
        assertEquals(before, tables.associateWith { count(f.payroll.company, it) })
        payrollError(get(f.maker.client, amendmentPath(f)), 403, "access_denied")
        for (member in
            listOf(f.payroll.owner, f.payroll.operator, f.publication.finalizer)) payrollError(
            get(member.client, base),
            403,
            "access_denied",
        )
        assertEquals(1, amendmentPreparation(f)["sourceVersions"].size())
    }

    @Test
    fun currentBeneficiariesAndForeignCompanyRecordsAreExcludedFromDiscovery() {
        val f = amendmentFixture()
        val base = "/api/v1/companies/${f.payroll.company}/payroll/amendment-sources?year=2026"
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.amend')",
                f.payroll.company,
                f.payroll.owner.account,
            )
        assertEquals(0, payrollBody(get(f.payroll.owner.client, base))["items"].size())
        val foreign = payrollFixture()
        val visitor = payrollMember(foreign.company, setOf("company.read", "payroll.amend"))
        payrollError(get(visitor.client, base), 403, "company_access_denied")
        assertEquals(
            0,
            payrollBody(
                    get(
                        visitor.client,
                        "/api/v1/companies/${foreign.company}/payroll/amendment-sources?year=2026",
                    )
                )["items"]
                .size(),
        )
        assertEquals(
            0,
            payrollBody(get(f.maker.client, "$base&employeeId=${UUID.randomUUID()}"))["items"]
                .size(),
        )
        assertEquals(1, payrollBody(get(f.maker.client, base))["items"].size())
    }

    @Test
    fun initialAccountBindingExcludesTheNewBeneficiaryFromDiscovery() {
        val payroll = payrollFixture(bindAccount = false)
        val f = amendmentFixture(calculationFixture(f = processingFixture(payroll)))
        val base = "/api/v1/companies/${payroll.company}/payroll/amendment-sources?year=2026"
        assertEquals(1, payrollBody(get(f.maker.client, base))["items"].size())
        payrollBody(
            command(
                payroll.admin,
                "/api/v1/companies/${payroll.company}/employees/${payroll.employee}/account-link",
                json.writeValueAsString(
                    mapOf(
                        "accountId" to f.maker.account,
                        "expectedVersion" to 0,
                        "reason" to "Initial verified employee account",
                    )
                ),
                payroll.adminCsrf,
                UUID.randomUUID(),
            )
        )
        assertEquals(0, payrollBody(get(f.maker.client, base))["items"].size())
        payrollError(
            get(f.maker.client, amendmentPath(f) + "/amendment-preparation"),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
    }

    @Test
    fun annualDiscoveryHasStablePagesAndLiteralEmployeeNameOrNumberSearch() {
        val f = amendmentFixture()
        val second = followingMonth(f.publication.calculation, YearMonth.of(2026, 10))
        val (_, october) = publishTaxRun(second, beginTaxRun(second))
        login(f.maker.client, "${f.maker.account}@example.test")
        val base = "/api/v1/companies/${f.payroll.company}/payroll/amendment-sources?year=2026"
        val all = payrollBody(get(f.maker.client, base))["items"].toList()
        val expected = listOf(f.assessment.toString(), october.toString()).sorted()
        assertEquals(expected, all.map { it["assessmentId"].asString() })
        assertEquals(setOf("2026-09", "2026-10"), all.map { it["taxMonth"].asString() }.toSet())
        val first = payrollBody(get(f.maker.client, "$base&limit=1"))
        assertEquals(expected[0], first["items"][0]["assessmentId"].asString())
        assertEquals(expected[0], first["nextCursor"].asString())
        val last =
            payrollBody(
                get(f.maker.client, "$base&limit=1&after=${first["nextCursor"].asString()}")
            )
        assertEquals(expected[1], last["items"][0]["assessmentId"].asString())
        assertTrue(last["nextCursor"].isNull)
        assertEquals(
            0,
            payrollBody(get(f.maker.client, "$base&after=${expected[1]}"))["items"].size(),
        )
        assertEquals(
            0,
            payrollBody(get(f.maker.client, base.replace("year=2026", "year=2025")))["items"].size(),
        )
        val employee = all[0]["employeeId"].asString()
        for (field in listOf("employeeNumber", "employeeName")) {
            val query =
                URLEncoder.encode(
                    "  ${all[0][field].asString().lowercase()}  ",
                    StandardCharsets.UTF_8,
                )
            assertEquals(
                expected,
                payrollBody(get(f.maker.client, "$base&employeeId=$employee&query=$query"))["items"]
                    .toList()
                    .map { it["assessmentId"].asString() },
            )
        }
        for (literal in listOf("%25", "%5F")) assertEquals(
            0,
            payrollBody(get(f.maker.client, "$base&query=$literal"))["items"].size(),
        )
    }

    @Test
    fun requestBoundsAreCheckedBeforeAcquiringPrivateCandidates() {
        val f = amendmentFixture()
        val base = "/api/v1/companies/${f.payroll.company}/payroll/amendment-sources"
        var acquired = false
        amendmentProbe.afterRead = { company -> if (company == f.payroll.company) acquired = true }
        for (query in
            listOf(
                "year=2023",
                "year=2101",
                "year=2026&limit=0",
                "year=2026&limit=101",
                "year=2026&query=${"x".repeat(121)}",
                "year=2026&query=line%0Abreak",
            )) payrollError(get(f.maker.client, "$base?$query"), 422, "invalid_page")
        assertFalse(acquired)
        assertEquals(
            1,
            payrollBody(get(f.maker.client, "$base?year=2026&limit=100"))["items"].size(),
        )
        assertTrue(acquired)
    }

    @Test
    fun malformedDiscoveryRowsAndTechnicalFailuresStayInsideTheDatabaseBoundary() {
        val f = amendmentFixture()
        val base = "/api/v1/companies/${f.payroll.company}/payroll/amendment-sources?year=2026"
        val original = payrollBody(get(f.maker.client, base))
        val scoped = "$base&employeeId=${original["items"][0]["employeeId"].asString()}"
        for (kind in
            listOf(
                "company",
                "employee",
                "year",
                "day",
                "revision",
                "duplicate",
                "bound",
                "label",
            )) {
            amendmentProbe.candidateTransform = { company, rows ->
                if (company != f.payroll.company) rows
                else
                    when (kind) {
                        "company" -> rows.map { it.copy(companyId = UUID.randomUUID()) }
                        "employee" -> rows.map { it.copy(employeeId = UUID.randomUUID()) }
                        "year" -> rows.map { it.copy(taxMonth = it.taxMonth.minusYears(1)) }
                        "day" -> rows.map { it.copy(taxMonth = it.taxMonth.plusDays(1)) }
                        "revision" -> rows.map { it.copy(revision = -1) }
                        "duplicate" -> rows + rows
                        "bound" -> List(52) { rows.first() }
                        "label" ->
                            rows.map {
                                it.copy(employeeName = "private-discovery-sentinel".repeat(20))
                            }
                        else -> error("Unknown row corruption")
                    }
            }
            val response = get(f.maker.client, scoped)
            payrollError(response, 500, "database_failure")
            assertFalse(response.body().contains("private-discovery-sentinel"))
            assertFalse(response.body().contains("IllegalArgumentException"))
        }
        amendmentProbe.candidateTransform = { _, _ ->
            throw TransientDataAccessResourceException("private-driver-sentinel")
        }
        val unavailable = get(f.maker.client, base)
        payrollError(unavailable, 503, "database_busy")
        assertFalse(unavailable.body().contains("private-driver-sentinel"))
        amendmentProbe.clear()
        assertEquals(original, payrollBody(get(f.maker.client, base)))
    }
}
