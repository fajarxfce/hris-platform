package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.entities.OvertimeInterval
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimeAccessHttpTest : OvertimeApiFixture() {
    @Test
    fun everyMakerAndBeneficiaryIsExcludedEvenAfterReassignment() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        overtimeBody(plan(f, id, admin = true))
        overtimeBody(actual(f, id))
        var view = details(f, id)
        assertEquals("BLOCKED", view["approval"]["status"].asString())
        val approval = UUID.fromString(view["approval"]["id"].asString())
        overtimeBody(reassign(f, approval, setOf(f.actor.accountId)))
        assertCode(decide(f, id), 403, "self_approval_denied")
        view = details(f, id, f.admin)
        assertFalse(view["actions"].iterator().asSequence().any { it.asString() == "APPROVE" })
        val independent = reviewer(f)
        overtimeBody(reassign(f, approval, setOf(independent.id), 1))
        overtimeBody(decide(f, id, by = independent))
        assertEquals("APPROVED", details(f, id)["request"]["status"].asString())
    }

    @Test
    fun selfAndAssignedReadersCannotBrowseUnrelatedEmployeeRequests() {
        val f = overtimeFixture()
        val id = pending(f)
        val unrelated = reviewer(f)
        assertEquals(404, get(unrelated.client, "${f.path}/overtime/$id").statusCode())
        assertEquals(
            403,
            get(unrelated.client, "${f.path}/overtime?from=2026-09-01&until=2026-09-30")
                .statusCode(),
        )
        assertEquals(
            404,
            get(
                    unrelated.client,
                    "${f.path}/overtime?employeeId=${f.employee}&from=2026-09-01&until=2026-09-30",
                )
                .statusCode(),
        )
        val own =
            overtimeBody(
                get(
                    f.employeeClient,
                    "${f.path}/overtime?employeeId=${f.employee}&from=2026-09-01&until=2026-09-30",
                )
            )
        assertEquals(1, own["items"].size())
        val approval = UUID.fromString(details(f, id)["approval"]["id"].asString())
        overtimeBody(reassign(f, approval, setOf(unrelated.id)))
        assertEquals(id.toString(), details(f, id, unrelated.client)["request"]["id"].asString())
        assertEquals(
            404,
            get(
                    unrelated.client,
                    "${f.path}/overtime?employeeId=${f.employee}&from=2026-09-01&until=2026-09-30",
                )
                .statusCode(),
        )
        assertCode(
            get(f.admin, "${f.path}/overtime?from=2025-01-01&until=2026-09-30"),
            422,
            "invalid_overtime_page",
        )
    }

    @Test
    fun permissionRevocationDuringEveryCommandPreventsMutationAndReplay() {
        for (action in listOf("plan", "actual", "withdraw", "decide", "close")) {
            val f = overtimeFixture()
            val id = UUID.randomUUID()
            val key = UUID.randomUUID()
            if (action in setOf("actual", "withdraw", "decide")) overtimeBody(plan(f, id))
            if (action == "decide") overtimeBody(actual(f, id))
            val account =
                if (action in setOf("decide", "close")) f.actor.accountId else f.employeeAccount
            val permission =
                when (action) {
                    "decide" -> "overtime.approve"
                    "close" -> "workforce.close"
                    else -> "overtime.self.manage"
                }
            val barrier = AccountLockProbe.Barrier(account)
            accountProbe.current.set(barrier)
            val receipts = rows(f, "operation_receipts")
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<java.net.http.HttpResponse<String>> {
                        when (action) {
                            "plan" -> plan(f, id, key)
                            "actual" -> actual(f, id, key)
                            "withdraw" -> withdraw(f, id, 0, key)
                            "decide" -> decide(f, id, key = key)
                            else -> close(f, key = key)
                        }
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                            f.company,
                            account,
                            permission,
                        )
                    barrier.release.countDown()
                    assertCode(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(receipts, rows(f, "operation_receipts"))
            assertEquals(0, rows(f, "background_jobs"))
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    account,
                    permission,
                )
            overtimeBody(
                when (action) {
                    "plan" -> plan(f, id, key)
                    "actual" -> actual(f, id, key)
                    "withdraw" -> withdraw(f, id, 0, key)
                    "decide" -> decide(f, id, key = key)
                    else -> close(f, key = key)
                }
            )
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                    f.company,
                    account,
                    permission,
                )
            assertEquals(
                403,
                when (action) {
                    "plan" -> plan(f, id, key)
                    "actual" -> actual(f, id, key)
                    "withdraw" -> withdraw(f, id, 0, key)
                    "decide" -> decide(f, id, key = key)
                    else -> close(f, key = key)
                }.statusCode(),
            )
        }
    }

    @Test
    fun credentialRevocationWhileActualTimeWaitsCannotPublishAnApproval() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        overtimeBody(plan(f, id))
        val barrier = AccountLockProbe.Barrier(f.employeeAccount)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<java.net.http.HttpResponse<String>> { actual(f, id) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        f.employeeAccount,
                    )
                barrier.release.countDown()
                assertCode(pending.get(10, TimeUnit.SECONDS), 401, "session_revoked")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, rows(f, "approval_requests"))
        assertEquals("PLANNED", details(f, id, f.admin)["request"]["status"].asString())
    }

    @Test
    fun newlyGrantedCompanyReadCannotExpandAnAlreadyResolvedRequest() {
        val f = overtimeFixture()
        val id = pending(f)
        val reader = reviewer(f, setOf("company.read"))
        val barrier = AccountLockProbe.Barrier(reader.id)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> {
                    get(reader.client, "${f.path}/overtime/$id")
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'overtime.read')",
                        f.company,
                        reader.id,
                    )
                barrier.release.countDown()
                assertEquals(404, pending.get(10, TimeUnit.SECONDS).statusCode())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(id.toString(), details(f, id, reader.client)["request"]["id"].asString())
    }

    @Test
    fun cancellationAfterTheInsertRollsBackSyncHistoryAndReceipts() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val receipts = rows(f, "operation_receipts")
        overtimeProbe.beforeJournal = {
            if (it.action == "overtime.planned") {
                Thread.currentThread().interrupt()
                throw InterruptedException("Fixture cancellation")
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    planUseCase.execute(
                        f.actor,
                        key,
                        id,
                        f.employee,
                        0,
                        LocalDate.parse("2026-09-01"),
                        OvertimeInterval(
                            Instant.parse("2026-09-01T01:00:00Z"),
                            Instant.parse("2026-09-01T04:00:00Z"),
                            0,
                        ),
                        "Inventory count",
                    )
                }
            val error =
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
            assertTrue(error.cause is InterruptedException)
        }
        assertEquals(0, rows(f, "overtime_requests"))
        assertEquals(0, rows(f, "overtime_changes"))
        assertEquals(0, rows(f, "mobile_sync_changes"))
        assertEquals(receipts, rows(f, "operation_receipts"))
        overtimeProbe.clear()
        val retried =
            planUseCase.execute(
                f.actor,
                key,
                id,
                f.employee,
                0,
                LocalDate.parse("2026-09-01"),
                OvertimeInterval(
                    Instant.parse("2026-09-01T01:00:00Z"),
                    Instant.parse("2026-09-01T04:00:00Z"),
                    0,
                ),
                "Inventory count",
            )
        assertTrue(retried is Result.Success, retried.toString())
    }
}
