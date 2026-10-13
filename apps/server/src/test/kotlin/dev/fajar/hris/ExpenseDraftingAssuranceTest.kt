package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReadScope
import dev.fajar.hris.expenses.domain.entities.ExpenseDraftReferenceQuery
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ExpenseDraftingAssuranceTest : ExpenseReadAssuranceApiFixture() {
    @ParameterizedTest
    @CsvSource(
        "employees,false",
        "employees,true",
        "references,false",
        "references,true",
        "receipts,false",
        "receipts,true",
    )
    fun expiredAssuranceCannotPublishAdministrativeReferences(kind: String, acquired: Boolean) {
        val scene = managementScene()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val account = AccountLockProbe.Barrier(actor.accountId)
        val readBarrier = ExpenseReadProbe.Barrier(scene.company, probeKind(kind))
        if (acquired) readProbe.current.set(readBarrier) else accountProbe.current.set(account)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, scene, actor) }
            try {
                assertTrue(
                    (if (acquired) readBarrier.entered else account.entered).await(
                        5,
                        TimeUnit.SECONDS,
                    )
                )
                clock.set(clock.instant().plusSeconds(2))
                readBarrier.release.countDown()
                account.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                readBarrier.release.countDown()
                account.release.countDown()
                readProbe.current.set(null)
                accountProbe.current.set(null)
            }
        }
        assertTrue(read(kind, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["employees", "references", "receipts"])
    fun originalAndCurrentManagementGrantsBothConstrainDiscovery(kind: String) {
        val scene = managementScene()
        assertEquals(
            "access_denied",
            (read(
                    kind,
                    scene,
                    scene.actor.copy(permissions = scene.actor.permissions - "expenses.manage"),
                )
                    as Result.Failed)
                .failure
                .code,
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.manage'",
                scene.company,
                scene.actor.accountId,
            )
        assertEquals(
            "access_denied",
            (read(kind, scene, scene.actor) as Result.Failed).failure.code,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["employees", "references", "receipts"])
    fun cancelledDiscoveryReleasesGuardsAndDoesNotPoisonItsNextRequest(kind: String) {
        val scene = managementScene()
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, scene, scene.actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertThrows(CancellationException::class.java) {
                    pending.get(10, TimeUnit.SECONDS)
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            assertTrue(
                pool.submit<Result<*>> { read(kind, scene, scene.actor) }.get(10, TimeUnit.SECONDS)
                    is Result.Success
            )
            assertTrue(
                pool
                    .submit<Result<*>> { acquireReadGuardsExclusively(scene) }
                    .get(10, TimeUnit.SECONDS) is Result.Success
            )
        }
    }

    private fun managementScene(): Scenario {
        val scene = prepare()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.manage')",
                scene.company,
                scene.actor.accountId,
            )
        return scene.copy(
            actor = scene.actor.copy(permissions = scene.actor.permissions + "expenses.manage")
        )
    }

    private fun probeKind(kind: String) =
        when (kind) {
            "employees" -> "directory"
            "references" -> "receipt-metadata"
            "receipts" -> "draft-receipts"
            else -> error("Unknown drafting read")
        }

    private fun read(kind: String, scene: Scenario, actor: Actor): Result<*> =
        when (kind) {
            "employees" -> draftEmployeeReader().execute(actor, "", null, 20)
            "references" ->
                draftReferenceReader()
                    .execute(
                        actor,
                        scene.fixture.employee,
                        ExpenseDraftReferenceQuery(receiptRevisionIds = setOf(scene.revision)),
                        ExpenseClaimReadScope.AUTHORIZED,
                    )
            "receipts" -> draftReceiptReader().execute(actor, scene.fixture.employee, null, 20)
            else -> error("Unknown drafting read")
        }
}
