package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPeriodReviewHttpTest : PayrollPeriodApiFixture() {
    @Test
    fun discoveryAndRosterExposeOnlyMinimalPayrollReferencesWithFinitePages() {
        val f = processingFixture()
        val other =
            employee(f.payroll.admin, f.payroll.adminCsrf, f.payroll.company, start = "2027-01-01")
        val path = periodsPath(f) + "/employees"
        val first = payrollBody(get(f.preparer.client, "$path?limit=1"))
        val next =
            payrollBody(
                get(f.preparer.client, "$path?limit=1&after=${first["nextCursor"].asString()}")
            )
        assertTrue(next["nextCursor"].isNull)
        val references = listOf(first["items"][0], next["items"][0])
        assertEquals(
            setOf(f.payroll.employee.toString(), other.toString()),
            references.map { it["id"].asString() }.toSet(),
        )
        for (reference in references) assertEquals(
            setOf("id", "name", "employeeNumber", "employmentVersion"),
            reference.propertyNames().toSet(),
        )
        assertEquals(0, payrollBody(get(f.reviewer.client, "$path?query=%25"))["items"].size())
        val number = first["items"][0]["employeeNumber"].asString()
        assertEquals(1, payrollBody(get(f.reviewer.client, "$path?query=$number"))["items"].size())
        val id = UUID.randomUUID()
        payrollBody(periodCreate(f, periodBody(f, id, setOf(f.payroll.employee, other))))
        val detail =
            payrollBody(get(f.reviewer.client, periodsPath(f) + "/$id?limit=1&historyLimit=1"))
        val row = detail["members"]["items"][0]
        val reference = references.single { it["id"] == row["employeeId"] }
        assertEquals(reference["name"], row["name"])
        assertEquals(reference["employeeNumber"], row["employeeNumber"])
        assertEquals(
            setOf("employeeId", "name", "employeeNumber", "inputVersion", "inputStatus"),
            row.propertyNames().toSet(),
        )
        assertEquals(1, detail["history"]["items"].size())
        payrollError(
            get(f.reviewer.client, periodsPath(f) + "/$id?historyAfter=1"),
            422,
            "invalid_page",
        )
        for (query in
            listOf("limit=0", "limit=51", "query=" + "a".repeat(121), "after=" + "x".repeat(33))) {
            payrollError(get(f.reviewer.client, "$path?$query"), 422, "invalid_page")
        }
    }

    @Test
    fun discoveryUsesPayrollPeriodAuthorityWithoutPrivatePeopleOrCompensationGrants() {
        val f = processingFixture()
        val route = periodsPath(f) + "/employees"
        payrollBody(get(f.preparer.client, route))
        payrollBody(get(f.reviewer.client, route))
        payrollError(get(f.payroll.owner.client, route), 403, "access_denied")
        val salaryManager =
            payrollMember(f.payroll.company, setOf("company.read", "payroll.compensation.manage"))
        payrollError(get(salaryManager.client, route), 403, "access_denied")
        val outsider = processingFixture()
        assertEquals(403, get(outsider.preparer.client, route).statusCode())
        payrollError(
            get(
                f.preparer.client,
                "/api/v1/companies/${f.payroll.company}/employees/${f.payroll.employee}/profile",
            ),
            404,
            "person_profile_not_found",
        )
        payrollError(
            get(
                f.preparer.client,
                "/api/v1/companies/${f.payroll.company}/payroll/compensation-employees",
            ),
            403,
            "access_denied",
        )
    }

    @Test
    fun newlyGrantedReadAuthorityCannotReplaceRevokedOriginalAuthorityDuringDiscovery() {
        val f = processingFixture()
        val reader = payrollMember(f.payroll.company, setOf("company.read", "payroll.calculate"))
        val barrier = AccountLockProbe.Barrier(reader.account)
        accountProbe.current.set(barrier)
        val route = periodsPath(f) + "/employees"
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> { get(reader.client, route) }
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
        payrollBody(get(reader.client, route))
    }
}
