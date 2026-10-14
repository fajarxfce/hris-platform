package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollTaxOpeningReviewHttpTest : PayrollTaxOpeningApiFixture() {
    @Test
    fun discoveryIncludesMissingHistoryAndFutureEmployeesWithMinimalBoundedReferences() {
        val f = openingFixture()
        val future =
            employee(f.payroll.admin, f.payroll.adminCsrf, f.payroll.company, start = "2027-01-01")
        val path = "/api/v1/companies/${f.payroll.company}/payroll/tax-openings/employees?year=2026"
        val first = payrollBody(get(f.preparer.client, "$path&limit=1"))
        val second =
            payrollBody(
                get(f.reviewer.client, "$path&limit=1&after=${first["nextCursor"].asString()}")
            )
        val rows = listOf(first["items"][0], second["items"][0])
        assertEquals(
            setOf(f.payroll.employee.toString(), future.toString()),
            rows.map { it["id"].asString() }.toSet(),
        )
        assertTrue(second["nextCursor"].isNull)
        for (row in rows) {
            assertEquals(
                setOf(
                    "id",
                    "name",
                    "employeeNumber",
                    "employmentVersion",
                    "openingVersion",
                    "openingStatus",
                ),
                row.propertyNames().toSet(),
            )
            assertTrue(row["openingVersion"].isNull)
            assertTrue(row["openingStatus"].isNull)
        }
        assertEquals(0, payrollBody(get(f.preparer.client, "$path&query=%25"))["items"].size())
        val number = first["items"][0]["employeeNumber"].asString()
        assertEquals(1, payrollBody(get(f.preparer.client, "$path&query=$number"))["items"].size())
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "query=" + "x".repeat(121),
                "after=" + "x".repeat(33),
            )) payrollError(get(f.preparer.client, "$path&$query"), 422, "invalid_page")
        payrollError(get(f.preparer.client, path.replace("2026", "2101")), 422, "invalid_page")
        payrollBody(opening(f))
        assertEquals(1, payrollBody(get(f.preparer.client, "$path&status=DRAFT"))["items"].size())
        assertEquals(
            future.toString(),
            payrollBody(get(f.preparer.client, "$path&status=UNPREPARED"))["items"][0]["id"]
                .asString(),
        )
        payrollBody(verify(f))
        val verified = payrollBody(get(f.reviewer.client, "$path&status=VERIFIED"))["items"][0]
        assertEquals(1, verified["openingVersion"].asLong())
        assertEquals("VERIFIED", verified["openingStatus"].asString())
        assertEquals(0, payrollBody(get(f.preparer.client, "$path&status=DRAFT"))["items"].size())
        payrollError(get(f.payroll.owner.client, path), 403, "access_denied")
    }

    @Test
    fun reviewSeparatesMissingHistoryFromZeroAndPreservesIndependentHistoricalReview() {
        val f = openingFixture()
        val own =
            employee(
                f.payroll.admin,
                f.payroll.adminCsrf,
                f.payroll.company,
                account = f.preparer.account,
            )
        val path = openingPath(f) + "/review"
        val empty = payrollBody(get(f.preparer.client, path))
        assertTrue(empty["current"].isNull)
        assertEquals(0, empty["history"]["items"].size())
        assertEquals(
            listOf("PREPARE"),
            empty["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(
            setOf("id", "name", "employeeNumber", "employmentVersion"),
            empty["employee"].propertyNames().toSet(),
        )
        assertEquals(0, payrollBody(get(f.reviewer.client, path))["availableActions"].size())
        assertEquals(
            0,
            payrollBody(get(f.preparer.client, openingPath(f, own) + "/review"))["availableActions"]
                .size(),
        )
        payrollBody(
            opening(
                f,
                openingBody(
                    termChanges = mapOf("throughMonth" to 0),
                    historyChanges =
                        mapOf(
                            "taxableGross" to "0",
                            "retirementContributions" to "0",
                            "withheld" to "0",
                            "employmentMonths" to 0,
                        ),
                ),
            )
        )
        val prepared = payrollBody(get(f.preparer.client, path))
        assertEquals(0, prepared["current"]["terms"]["throughMonth"].asInt())
        assertEquals(
            listOf("PREPARE"),
            prepared["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        val checked = payrollBody(get(f.reviewer.client, path))
        assertEquals(
            listOf("VERIFY"),
            checked["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertFalse(checked["history"]["items"][0].has("terms"))
        val next =
            payrollMember(
                f.payroll.company,
                setOf("company.read", "payroll.calculate", "payroll.review"),
            )
        payrollBody(opening(f, openingBody(0), member = next))
        val historicalAuthor = payrollBody(get(f.preparer.client, path))
        assertEquals(
            listOf("PREPARE"),
            historicalAuthor["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        val page = payrollBody(get(f.reviewer.client, "$path?historyLimit=1"))
        assertEquals(1, page["current"]["version"].asLong())
        assertEquals("0", page["history"]["nextCursor"].asString())
        val remaining = payrollBody(get(f.reviewer.client, "$path?historyAfter=0&historyLimit=1"))
        assertEquals(1, remaining["history"]["items"][0]["version"].asLong())
        assertTrue(remaining["history"]["nextCursor"].isNull)
        val original = payrollBody(get(f.reviewer.client, openingPath(f) + "?revision=0"))
        assertEquals(prepared["current"], original)
        payrollError(
            get(f.reviewer.client, openingPath(f) + "?revision=2"),
            404,
            "payroll_tax_opening_not_found",
        )
        payrollError(get(f.reviewer.client, openingPath(f) + "?revision=1000"), 422, "invalid_page")
        for (query in
            listOf(
                "historyAfter=2",
                "historyAfter=-1",
                "historyLimit=0",
                "historyLimit=201",
            )) payrollError(get(f.reviewer.client, "$path?$query"), 422, "invalid_page")
        assertTrue(
            payrollBody(get(f.reviewer.client, openingPath(f, year = 2027) + "/review"))["current"]
                .isNull
        )
        payrollBody(verify(f, version = 1))
        assertEquals(0, payrollBody(get(f.reviewer.client, path))["availableActions"].size())
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
        assertEquals(
            1,
            payrollBody(get(f.preparer.client, path))["employee"]["employmentVersion"].asLong(),
        )
        payrollError(opening(f, openingBody(2)), 409, "stale_employment_version")
        payrollError(get(f.payroll.owner.client, path), 403, "access_denied")
        payrollError(
            get(
                f.preparer.client,
                "/api/v1/companies/${f.payroll.company}/employees/${f.payroll.employee}/profile",
            ),
            404,
            "person_profile_not_found",
        )
        val outsider = openingFixture()
        assertEquals(403, get(outsider.preparer.client, path).statusCode())
        payrollError(
            get(f.preparer.client, openingPath(f, outsider.payroll.employee) + "/review"),
            404,
            "employee_not_found",
        )
    }

    @Test
    fun concurrentReadersShareTheSnapshotGuardWhileARevisionWaits() {
        val f = openingFixture()
        payrollBody(opening(f))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)
        val held = AtomicBoolean()
        openingProbe.afterRead = { kind, company ->
            if (
                company == f.payroll.company && kind == "detail" && held.compareAndSet(false, true)
            ) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        payrollProbe.beforePolicyLock = { company, shared ->
            if (company == f.payroll.company && !shared) writerEntered.countDown()
        }
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    get(f.reviewer.client, openingPath(f) + "/review")
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(
                    0,
                    payrollBody(get(f.reviewer.client, openingPath(f) + "/review"))["current"][
                            "version"]
                        .asLong(),
                )
                val writer = pool.submit<HttpResponse<String>> { opening(f, openingBody(0)) }
                assertTrue(writerEntered.await(5, TimeUnit.SECONDS))
                release.countDown()
                val original = payrollBody(pending.get(10, TimeUnit.SECONDS))
                assertEquals(0, original["current"]["version"].asLong())
                assertEquals(1, original["history"]["items"].size())
                assertEquals(1, payrollBody(writer.get(10, TimeUnit.SECONDS))["version"].asLong())
            } finally {
                release.countDown()
                payrollProbe.clear()
                openingProbe.clear()
            }
        }
    }

    @Test
    fun discoveryAndReviewIntersectTheOriginalAndLiveGrantsAfterWaiting() {
        val f = openingFixture()
        for (path in
            listOf(
                "/api/v1/companies/${f.payroll.company}/payroll/tax-openings/employees?year=2026",
                openingPath(f) + "/review",
            )) {
            val reader =
                payrollMember(f.payroll.company, setOf("company.read", "payroll.calculate"))
            val barrier = AccountLockProbe.Barrier(reader.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<HttpResponse<String>> { get(reader.client, path) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.calculate'",
                            f.payroll.company,
                            reader.account,
                        )
                    database()
                        .update(
                            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.review')",
                            f.payroll.company,
                            reader.account,
                        )
                    barrier.release.countDown()
                    payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            payrollBody(get(reader.client, path))
        }
    }
}
