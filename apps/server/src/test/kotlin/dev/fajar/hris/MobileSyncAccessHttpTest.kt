package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.domain.entities.SyncCollection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MobileSyncAccessHttpTest : MobileSyncApiFixture() {
    @Test
    fun cursorCannotBeReusedByAnotherAccountOrCompany() {
        val f = expenseFixture()
        val cursor = token(f)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.self.manage')",
                f.company,
                f.managerAccount,
            )
        error(
            get(
                f.supervisor,
                "/api/v1/companies/${f.company}/sync/changes?cursor=${encode(cursor)}",
            ),
            422,
            "invalid_sync_cursor",
        )
        val other = expenseFixture()
        error(changes(other, cursor), 422, "invalid_sync_cursor")
        error(
            get(f.worker, "/api/v1/companies/${other.company}/sync/bootstrap"),
            403,
            "company_access_denied",
        )
    }

    @Test
    fun membershipRevocationAndRestorationRequireANewLocalPartition() {
        val f = expenseFixture()
        val old = token(f)
        database()
            .update(
                "update company_memberships set version=version+1 where company_id=? and account_id=?",
                f.company,
                f.account,
            )
        error(changes(f, old), 409, "sync_scope_changed")
        val current = token(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.self.manage'",
                f.company,
                f.account,
            )
        error(changes(f, current), 403, "sync_access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.self.manage')",
                f.company,
                f.account,
            )
        database()
            .update(
                "update company_memberships set version=version+1 where company_id=? and account_id=?",
                f.company,
                f.account,
            )
        error(changes(f, current), 409, "sync_scope_changed")
        val restored = token(f)
        database()
            .update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                f.company,
                f.account,
            )
        error(changes(f, restored), 403, "company_access_denied")
    }

    @Test
    fun ownershipChangesResetTheCursorAndExcessiveOwnershipFailsBeforeExport() {
        val f = expenseFixture()
        val cursor = token(f)
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) select company_id,gen_random_uuid(),person_id,'REJOIN' from employments where company_id=? and id=?",
                f.company,
                f.employee,
            )
        error(changes(f, cursor), 409, "sync_scope_changed")
        body(bootstrap(f))
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) select e.company_id,gen_random_uuid(),e.person_id,'HISTORY-'||n from employments e cross join generate_series(1,199) n where e.company_id=? and e.id=?",
                f.company,
                f.employee,
            )
        val limited = bootstrap(f)
        error(limited, 422, "sync_scope_limit")
        assertEquals(
            "200",
            json.readTree(limited.body()).get("parameters").get("maximumEmployments").asString(),
        )
    }

    @Test
    fun liveCredentialRevocationAndNewGrantsCannotUpgradeAResolvedRequest() {
        val f = expenseFixture()
        val initial = actor(f)
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<dev.fajar.hris.sync.domain.entities.SyncBootstrapPage>> {
                    bootstrapUseCase.execute(initial)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.self.manage')",
                        f.company,
                        f.account,
                    )
                barrier.release.countDown()
                assertEquals(
                    setOf(SyncCollection.EXPENSE_CLAIMS),
                    success(pending.get(10, TimeUnit.SECONDS)).collections,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        val expanded =
            success(
                bootstrapUseCase.execute(
                    initial.copy(permissions = initial.permissions + "leave.self.manage")
                )
            )
        assertEquals(
            setOf(
                SyncCollection.EXPENSE_CLAIMS,
                SyncCollection.LEAVE_REQUESTS,
                SyncCollection.LEAVE_BALANCES,
            ),
            expanded.collections,
        )
        database()
            .update("update accounts set security_version=security_version+1 where id=?", f.account)
        val revoked = bootstrapUseCase.execute(initial)
        assertTrue(revoked is Result.Failed)
        assertEquals(FailureKind.UNAUTHENTICATED, (revoked as Result.Failed).failure.kind)
    }

    @Test
    fun parallelReadersShareGuardsAndBothReleaseTheirTransactionResources() {
        val f = expenseFixture()
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        syncProbe.afterHead = { company ->
            if (company == f.company) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newFixedThreadPool(2).use { executor ->
            val requests =
                (1..2).map {
                    executor.submit<Result<dev.fajar.hris.sync.domain.entities.SyncBootstrapPage>> {
                        bootstrapUseCase.execute(actor(f))
                    }
                }
            try {
                assertTrue(
                    entered.await(4, TimeUnit.SECONDS),
                    "Both readers must reach the shared head before either finishes",
                )
                release.countDown()
                requests.forEach { success(it.get(10, TimeUnit.SECONDS)) }
            } finally {
                release.countDown()
                syncProbe.clear()
            }
        }
        body(saveExpense(f))
    }

    @Test
    fun cancellationAndLateExpiryDoNotReturnAUsableCursorOrKeepTheReadGuards() {
        val f = expenseFixture()
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        syncProbe.afterSnapshot = { selection ->
            if (selection.companyId == f.company) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        val request = Thread {
            try {
                bootstrapUseCase.execute(actor(f))
                failure.set(AssertionError("A cancelled snapshot returned"))
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                exited.countDown()
            }
        }
        try {
            request.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            request.interrupt()
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            request.join(1000)
            assertFalse(request.isAlive)
            assertTrue(failure.get() is InterruptedException, failure.get().toString())
        } finally {
            release.countDown()
            request.interrupt()
            request.join(5000)
            syncProbe.clear()
        }
        body(saveExpense(f))
        syncProbe.afterSnapshot = { selection ->
            if (selection.companyId == f.company) clock.set(clock.instant().plusSeconds(901))
        }
        error(bootstrap(f), 409, "sync_cursor_expired")
        syncProbe.clear()
        body(bootstrap(f))
    }
}
