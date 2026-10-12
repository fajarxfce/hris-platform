package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentInventoryRepository
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class DocumentAdministrationAssuranceTest : DocumentAssuranceApiFixture() {
    @Autowired private lateinit var inventory: DocumentInventoryRepository
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "retention",
                "retention-history",
                "policies",
                "policy-history",
                "inventory",
                "inventories",
                "attempts",
                "pages",
            ]
    )
    fun expiredProofCannotReadAdministrativeMetadata(operation: String) {
        val selection = administrationFixture()
        val actor = enrolled(selection.fixture, security)
        val invoke = administrationRead(operation, selection)
        clock.set(clock.instant().plusSeconds(2))
        assertEquals("mfa_required", (invoke(actor) as? Result.Failed)?.failure?.code)
        val renewed = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(renewed is Result.Success, renewed.toString())
        assertEquals(0, storageProbe.reads.get())
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "retention",
                "retention-history",
                "policies",
                "policy-history",
                "inventory",
                "inventories",
                "attempts",
                "pages",
            ]
    )
    fun proofIsCheckedAfterThePendingAccessGuard(operation: String) {
        val selection = administrationFixture()
        val actor = enrolled(selection.fixture, security)
        val invoke = administrationRead(operation, selection)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    assertEquals(
                        "mfa_required",
                        (pending.get(10, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
        assertTrue(invoke(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @CsvSource(
        "retention,permission",
        "retention,credentials",
        "inventory,permission",
        "inventory,credentials",
    )
    fun pendingReadersUseCurrentPermissionsAndCredentials(operation: String, change: String) {
        val selection = administrationFixture()
        val f = selection.fixture
        val actor = enrolled(f, security).copy(mfaVerifiedAt = clock.instant())
        val invoke = administrationRead(operation, selection)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (change == "permission")
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                f.company,
                                actor.accountId,
                                "documents.$operation",
                            )
                    else
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                actor.accountId,
                            )
                    barrier.release.countDown()
                    assertEquals(
                        if (change == "permission") "access_denied" else "session_revoked",
                        (pending.get(10, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["retention", "inventory"])
    fun currentGrantsCannotBroadenTheResolvedRequest(operation: String) {
        val selection = administrationFixture()
        val actor =
            enrolled(selection.fixture, security)
                .copy(permissions = emptySet(), mfaVerifiedAt = clock.instant())
        assertEquals(
            "access_denied",
            (administrationRead(operation, selection)(actor) as? Result.Failed)?.failure?.code,
        )
    }

    @Test
    fun interruptedReaderReleasesAccessGuardsForRecovery() {
        val selection = administrationFixture()
        val actor = enrolled(selection.fixture, security).copy(mfaVerifiedAt = clock.instant())
        val invoke = administrationRead("inventory", selection)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        val exited = CountDownLatch(1)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<Result<*>> {
                        try {
                            invoke(actor)
                        } finally {
                            exited.countDown()
                        }
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    assertTrue(pending.cancel(true))
                    assertTrue(exited.await(5, TimeUnit.SECONDS))
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
        assertTrue(invoke(actor) is Result.Success)
        assertEquals(
            Result.Success(Unit),
            transactions.run(actor) { members.lock(selection.fixture.company) },
        )
    }

    private data class Selection(
        val fixture: Fixture,
        val document: UUID,
        val policy: UUID,
        val inventory: UUID,
    )

    private fun administrationFixture(): Selection {
        val base = fixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                base.company,
                base.actor.accountId,
            )
        val f =
            base.copy(
                actor =
                    base.actor.copy(permissions = base.actor.permissions + "documents.retention")
            )
        val document = UUID.randomUUID()
        begin(f, document = document)
        val policy = UUID.randomUUID()
        val saved =
            command(
                f.browser,
                "${f.path}/retention-policies",
                json.writeValueAsString(
                    mapOf(
                        "policyId" to policy,
                        "classification" to "PERSONAL",
                        "retentionDays" to 30,
                        "reason" to "Approved retention policy",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, saved.statusCode(), saved.body())
        val run = UUID.randomUUID()
        val started =
            command(
                f.browser,
                "${f.path}/inventory",
                json.writeValueAsString(
                    mapOf("runId" to run, "reason" to "Review private storage")
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, started.statusCode(), started.body())
        return Selection(f, document, policy, run)
    }

    private fun administrationRead(operation: String, selection: Selection): (Actor) -> Result<*> =
        { actor ->
            when (operation) {
                "retention" ->
                    GetDocumentRetention(
                            retention,
                            documents,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, selection.document)
                "retention-history" ->
                    GetDocumentRetentionHistory(
                            retention,
                            documents,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, selection.document, null, 20)
                "policies" ->
                    GetDocumentRetentionPolicies(
                            retention,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor)
                "policy-history" ->
                    GetDocumentRetentionPolicyHistory(
                            retention,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, selection.policy, null, 20)
                "inventory" ->
                    GetDocumentInventory(
                            inventory,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, selection.inventory)
                "inventories" ->
                    ListDocumentInventories(
                            inventory,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, null, 20)
                "attempts" ->
                    GetDocumentInventoryAttempts(
                            inventory,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, selection.inventory)
                "pages" ->
                    GetDocumentInventoryPages(
                            inventory,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, selection.inventory, 0, 20)
                else -> error("Unknown administrative read")
            }
        }
}
