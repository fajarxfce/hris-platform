package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollInputReviewHttpTest : PayrollRunApiFixture() {
    @Test
    fun employeeDiscoveryIncludesUnpreparedAndFutureEmploymentsWithMinimalBoundedRows() {
        val f = processingFixture()
        val future =
            employee(f.payroll.admin, f.payroll.adminCsrf, f.payroll.company, start = "2027-01-01")
        val path = "/api/v1/companies/${f.payroll.company}/payroll/inputs/employees?month=2026-09"
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
                    "inputVersion",
                    "inputStatus",
                ),
                row.propertyNames().toSet(),
            )
            assertTrue(row["inputVersion"].isNull)
            assertTrue(row["inputStatus"].isNull)
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
        val source = closeWork(f)
        payrollBody(inputSave(f, source))
        assertEquals(1, payrollBody(get(f.preparer.client, "$path&status=DRAFT"))["items"].size())
        assertEquals(
            future.toString(),
            payrollBody(get(f.preparer.client, "$path&status=UNPREPARED"))["items"][0]["id"]
                .asString(),
        )
        payrollBody(inputVerify(f))
        val verified = payrollBody(get(f.reviewer.client, "$path&status=VERIFIED"))["items"][0]
        assertEquals(1, verified["inputVersion"].asLong())
        assertEquals("VERIFIED", verified["inputStatus"].asString())
        assertEquals(0, payrollBody(get(f.preparer.client, "$path&status=DRAFT"))["items"].size())
    }

    @Test
    fun reviewCombinesCurrentSourcesHistoryAndIndependentActionsWithoutPrivatePeopleGrants() {
        val f = processingFixture()
        val own =
            employee(
                f.payroll.admin,
                f.payroll.adminCsrf,
                f.payroll.company,
                account = f.preparer.account,
            )
        val reviewPath = inputPath(f) + "/review"
        val empty = payrollBody(get(f.preparer.client, reviewPath))
        assertTrue(empty["current"].isNull)
        assertEquals(0, empty["history"]["items"].size())
        assertEquals(0, empty["availableActions"].size())
        assertEquals(
            setOf("id", "name", "employeeNumber", "employmentVersion"),
            empty["employee"].propertyNames().toSet(),
        )
        val source = closeWork(f)
        assertEquals(
            listOf("PREPARE"),
            payrollBody(get(f.preparer.client, reviewPath))["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        assertEquals(
            0,
            payrollBody(get(f.preparer.client, inputPath(f, own) + "/review"))["availableActions"]
                .size(),
        )
        payrollBody(inputSave(f, source))
        val preparer = payrollBody(get(f.preparer.client, reviewPath))
        assertEquals(
            listOf("PREPARE"),
            preparer["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        val reviewed = payrollBody(get(f.reviewer.client, "$reviewPath?historyLimit=1"))
        assertEquals(
            listOf("VERIFY"),
            reviewed["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(source.job.toString(), reviewed["source"]["jobId"].asString())
        assertEquals(0, reviewed["employee"]["employmentVersion"].asLong())
        assertEquals(0, reviewed["current"]["version"].asLong())
        val next =
            payrollMember(
                f.payroll.company,
                setOf("company.read", "payroll.calculate", "payroll.review"),
            )
        payrollBody(
            inputSave(
                f,
                source,
                inputBody(source, 0, termChanges = mapOf("nonCashTaxable" to "500000")),
                member = next,
            )
        )
        val historicalAuthor = payrollBody(get(f.preparer.client, reviewPath))
        assertEquals(
            listOf("PREPARE"),
            historicalAuthor["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        val page = payrollBody(get(f.reviewer.client, "$reviewPath?historyLimit=1"))
        assertEquals(1, page["current"]["version"].asLong())
        assertEquals("0", page["history"]["nextCursor"].asString())
        val remaining =
            payrollBody(get(f.reviewer.client, "$reviewPath?historyAfter=0&historyLimit=1"))
        assertEquals(1, remaining["history"]["items"][0]["version"].asLong())
        assertTrue(remaining["history"]["nextCursor"].isNull)
        payrollError(get(f.reviewer.client, "$reviewPath?historyAfter=2"), 422, "invalid_page")
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
        val changed = payrollBody(get(f.reviewer.client, reviewPath))
        assertEquals(1, changed["employee"]["employmentVersion"].asLong())
        assertEquals(0, changed["current"]["employmentVersion"].asLong())
        assertEquals(0, changed["availableActions"].size())
        payrollError(get(f.payroll.owner.client, reviewPath), 403, "access_denied")
        payrollError(
            get(
                f.preparer.client,
                "/api/v1/companies/${f.payroll.company}/employees/${f.payroll.employee}/profile",
            ),
            404,
            "person_profile_not_found",
        )
        val outsider = processingFixture()
        assertEquals(403, get(outsider.preparer.client, reviewPath).statusCode())
        payrollError(
            get(f.preparer.client, inputPath(f, outsider.payroll.employee) + "/review"),
            404,
            "employee_not_found",
        )
    }

    @Test
    fun calculationCutoffsRemoveInputActionsWhilePreservingReviewedEvidence() {
        val scene = calculationFixture()
        val f = scene.people
        val path = inputPath(f) + "/review"
        val before = payrollBody(get(f.preparer.client, path))
        assertFalse(before["frozen"].asBoolean())
        assertEquals(
            listOf("PREPARE"),
            before["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        payrollBody(createRun(scene))
        val after = payrollBody(get(f.preparer.client, path))
        assertTrue(after["frozen"].asBoolean())
        assertEquals(0, after["availableActions"].size())
        assertEquals(before["current"], after["current"])
        assertEquals(before["history"], after["history"])
    }

    @Test
    fun aConcurrentInputRevisionCannotMixTheCurrentHeadWithItsHistory() {
        val f = processingFixture()
        val source = closeWork(f)
        payrollBody(inputSave(f, source))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)
        val held = AtomicBoolean()
        periodProbe.afterInputRead = { kind, company ->
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
            val read =
                pool.submit<HttpResponse<String>> {
                    get(f.reviewer.client, inputPath(f) + "/review")
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(
                    0,
                    payrollBody(get(f.reviewer.client, inputPath(f) + "/review"))["current"][
                            "version"]
                        .asLong(),
                )
                val write =
                    pool.submit<HttpResponse<String>> { inputSave(f, source, inputBody(source, 0)) }
                assertTrue(writerEntered.await(5, TimeUnit.SECONDS))
                release.countDown()
                val original = payrollBody(read.get(10, TimeUnit.SECONDS))
                assertEquals(0, original["current"]["version"].asLong())
                assertEquals(1, original["history"]["items"].size())
                assertEquals(1, payrollBody(write.get(10, TimeUnit.SECONDS))["version"].asLong())
            } finally {
                release.countDown()
                payrollProbe.clear()
                periodProbe.clear()
            }
        }
    }

    @Test
    fun discoveryAndReviewIntersectOriginalAndCurrentPayrollGrantsAfterWaiting() {
        val f = processingFixture()
        for (path in
            listOf(
                "/api/v1/companies/${f.payroll.company}/payroll/inputs/employees?month=2026-09",
                inputPath(f) + "/review",
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
