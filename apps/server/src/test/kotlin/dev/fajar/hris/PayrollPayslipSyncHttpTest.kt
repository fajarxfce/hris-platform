package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.net.URLEncoder
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class PayrollPayslipSyncHttpTest : PayrollFinalizationApiFixture() {
    @Test
    fun pendingPublicationIsInvisibleAndCommittedChangesContainOnlyOwnedInvalidations() {
        val f = approved()
        val p = f.calculation.people.payroll
        val path = "/api/v1/companies/${p.company}/sync"
        val publisher = mobileSyncTestWorker(postgres.jdbcUrl, database())
        val bootstrap = payrollBody(get(p.owner.client, "$path/bootstrap"))
        assertEquals(
            listOf("PAYROLL_PAYMENTS", "PAYSLIPS"),
            bootstrap["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(0, bootstrap["items"].size())
        val changes =
            "$path/changes?cursor=${URLEncoder.encode(bootstrap["changesCursor"].asString(),Charsets.UTF_8)}"
        val lease = beginFinalization(f)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        finalizationProbe.afterAssessments = {
            entered.countDown()
            check(release.await(8, TimeUnit.SECONDS))
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { stepFinalization(f, lease) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(publisher.maintain.execute() is Result.Success)
                assertEquals(0, payrollBody(get(p.owner.client, changes))["items"].size())
                assertEquals(
                    0,
                    payrollBody(
                            get(p.owner.client, "/api/v1/companies/${p.company}/payroll/payslips")
                        )["items"]
                        .size(),
                )
            } finally {
                release.countDown()
            }
            assertEquals(Result.Success(JobStep(1, true)), pending.get(10, TimeUnit.SECONDS))
        }
        finalizationProbe.clear()
        assertTrue(publisher.maintain.execute() is Result.Success)
        val delta = payrollBody(get(p.owner.client, changes))
        assertEquals(1, delta["items"].size())
        val item = delta["items"][0]
        assertEquals("PAYSLIPS", item["collection"].asString())
        assertEquals("UPSERT", item["operation"].asString())
        assertEquals(0, item["version"].asLong())
        assertFalse(item.has("takeHome"))
        assertEquals(
            200,
            get(
                    p.owner.client,
                    "/api/v1/companies/${p.company}/payroll/payslips/${item["id"].asString()}",
                )
                .statusCode(),
        )
        val snapshot = payrollBody(get(p.owner.client, "$path/bootstrap"))
        assertEquals(item["id"], snapshot["items"][0]["id"])
        val unbound = payrollMember(p.company, setOf("company.read", "payroll.self.read"))
        assertEquals(0, payrollBody(get(unbound.client, "$path/bootstrap"))["items"].size())
        assertTrue(stepFinalization(f, lease) is Result.Failed)
        assertEquals(1, count(p.company, "mobile_sync_changes"))
        payrollError(get(p.operator.client, "$path/bootstrap"), 403, "sync_access_denied")
    }

    @Test
    fun removalOfPayslipAccessInvalidatesTheExistingPartitionCursor() {
        val f = approved()
        val p = f.calculation.people.payroll
        assertTrue(stepFinalization(f, beginFinalization(f)) is Result.Success)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.self.manage')",
                p.company,
                p.owner.account,
            )
        val path = "/api/v1/companies/${p.company}/sync"
        val first = payrollBody(get(p.owner.client, "$path/bootstrap"))
        assertEquals(1, first["items"].size())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.self.read'",
                p.company,
                p.owner.account,
            )
        payrollError(
            get(
                p.owner.client,
                "$path/changes?cursor=${URLEncoder.encode(first["changesCursor"].asString(),Charsets.UTF_8)}",
            ),
            409,
            "sync_scope_changed",
        )
        val restarted = payrollBody(get(p.owner.client, "$path/bootstrap"))
        assertEquals(0, restarted["items"].size())
        assertEquals(
            listOf("EXPENSE_CLAIMS"),
            restarted["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        payrollError(
            get(
                p.owner.client,
                "/api/v1/companies/${p.company}/payroll/payslips/${first["items"][0]["id"].asString()}",
            ),
            403,
            "access_denied",
        )
    }
}
