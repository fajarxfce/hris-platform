package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MobileSyncCollectionHttpTest : MobileSyncApiFixture() {
    @Test
    fun fixedClientSelectionSurvivesPaginationAndNeverAddsNewerCollectionsImplicitly() {
        val f = expenseFixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.self.read')",
                f.company,
                f.account,
            )
        body(saveExpense(f))
        body(saveExpense(f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID())))
        val path = "/api/v1/companies/${f.company}/sync"
        val all = body(get(f.worker, "$path/bootstrap"))
        assertEquals(3, all["collections"].size())
        val selected = body(get(f.worker, "$path/bootstrap?collections=EXPENSE_CLAIMS&limit=1"))
        assertEquals(
            listOf("EXPENSE_CLAIMS"),
            selected["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        val next = encode(selected["nextCursor"].asString())
        error(get(f.worker, "$path/bootstrap?cursor=$next"), 409, "sync_scope_changed")
        val final = body(get(f.worker, "$path/bootstrap?collections=EXPENSE_CLAIMS&cursor=$next"))
        assertEquals(1, final["items"].size())
        val cursor = encode(final["changesCursor"].asString())
        error(
            get(f.worker, "$path/changes?cursor=$cursor&collections=PAYSLIPS"),
            409,
            "sync_scope_changed",
        )
        body(saveExpense(f, version = 0))
        success(publisher.publish())
        val changes = body(get(f.worker, "$path/changes?cursor=$cursor&collections=EXPENSE_CLAIMS"))
        assertTrue(items(changes).all { it["collection"].asString() == "EXPENSE_CLAIMS" })
        assertTrue(
            items(changes).any {
                it["id"].asString() == f.claim.toString() && it["version"].asLong() == 1L
            }
        )
    }

    @Test
    fun selectionUsesLiveAuthorizationAndDefaultCursorsRemainCompatible() {
        val f = expenseFixture()
        val original = encode(token(f))
        val path = "/api/v1/companies/${f.company}/sync"
        body(get(f.worker, "$path/changes?cursor=$original&collections=EXPENSE_CLAIMS"))
        val mixed = body(get(f.worker, "$path/bootstrap?collections=PAYSLIPS,EXPENSE_CLAIMS"))
        assertEquals(
            listOf("EXPENSE_CLAIMS"),
            mixed["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        error(get(f.worker, "$path/bootstrap?collections=PAYSLIPS"), 403, "sync_access_denied")
        error(get(f.worker, "$path/bootstrap?collections="), 422, "invalid_sync_collections")
        error(get(f.worker, "$path/bootstrap?collections=UNKNOWN"), 400, "invalid_request")
        error(get(f.admin, "$path/bootstrap?collections=EXPENSE_CLAIMS"), 403, "sync_access_denied")
    }

    @Test
    fun requestedCollectionDoesNotBypassARevocationWhileWaiting() {
        val f = expenseFixture()
        val cursor = encode(token(f))
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> {
                    get(
                        f.worker,
                        "/api/v1/companies/${f.company}/sync/changes?cursor=$cursor&collections=EXPENSE_CLAIMS",
                    )
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.self.manage'",
                        f.company,
                        f.account,
                    )
                barrier.release.countDown()
                error(pending.get(10, TimeUnit.SECONDS), 403, "sync_access_denied")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }
}
