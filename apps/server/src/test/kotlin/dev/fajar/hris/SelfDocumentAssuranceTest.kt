package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentClassification
import dev.fajar.hris.documents.domain.usecases.ListSelfDocumentEmployments
import dev.fajar.hris.documents.domain.usecases.ListSelfDocumentEvidence
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SelfDocumentAssuranceTest : SelfDocumentApiFixture() {
    private fun reader(
        operation: String,
        owner: Fixture,
        security: IdentitySecurityPolicy,
    ): (Actor) -> Result<*> =
        when (operation) {
            "employments" -> {
                val useCase =
                    ListSelfDocumentEmployments(
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                val invoke: (Actor) -> Result<*> = { actor -> useCase.execute(actor, null, 20) }
                invoke
            }
            "evidence" -> {
                val useCase =
                    ListSelfDocumentEvidence(
                        documents,
                        profiles,
                        people,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                val invoke: (Actor) -> Result<*> = { actor ->
                    useCase.execute(
                        actor,
                        owner.employee,
                        DocumentClassification.PERSONAL,
                        null,
                        20,
                    )
                }
                invoke
            }
            else -> error("Unknown test read")
        }

    @ParameterizedTest
    @ValueSource(strings = ["employments", "evidence"])
    fun expiredProofAfterWaitingCannotReleaseOwnedReferences(operation: String) {
        val owner = selfFixture().owner
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor = enrolled(owner, security)
        val invoke = reader(operation, owner, security)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                )
            } finally {
                accountProbe.current.set(null)
                barrier.release.countDown()
            }
        }
        val renewed = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(renewed is Result.Success, renewed.toString())
    }

    @ParameterizedTest
    @CsvSource(
        "employments,permission",
        "evidence,permission",
        "employments,credentials",
        "evidence,credentials",
        "employments,membership",
        "evidence,membership",
        "employments,replacement-grant",
        "evidence,replacement-grant",
    )
    fun authorityIsRecheckedAfterHttpAdmissionAndThePendingGuard(
        operation: String,
        change: String,
    ) {
        val owner = selfFixture().owner
        val barrier = AccountLockProbe.Barrier(owner.actor.accountId, occurrence = 2)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    if (operation == "employments")
                        get(owner.browser, "${owner.path}/me/employments")
                    else evidence(owner)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credentials" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                owner.actor.accountId,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                owner.company,
                                owner.actor.accountId,
                            )
                    else -> {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=?",
                                owner.company,
                                owner.actor.accountId,
                            )
                        if (change == "replacement-grant")
                            database()
                                .update(
                                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.read')",
                                    owner.company,
                                    owner.actor.accountId,
                                )
                    }
                }
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credentials") 401 else 403,
                    response.statusCode(),
                    response.body(),
                )
                assertFalse(response.body().contains("employeeNumber"))
                assertFalse(response.body().contains("revisionId"))
            } finally {
                accountProbe.current.set(null)
                barrier.release.countDown()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["employments", "evidence"])
    fun originalScopeCannotExpandToUseANewSelfGrant(operation: String) {
        val owner = selfFixture().owner
        val result =
            reader(operation, owner, IdentitySecurityPolicy(enforceMfa = false))(
                owner.actor.copy(permissions = setOf("documents.read"))
            )
        assertEquals("access_denied", (result as? Result.Failed)?.failure?.code)
    }

    @ParameterizedTest
    @ValueSource(strings = ["employments", "evidence"])
    fun interruptionReleasesResourceGuardsForTheNextWriterAndRead(operation: String) {
        val (administrator, owner) = selfFixture()
        val invoke = reader(operation, owner, IdentitySecurityPolicy(enforceMfa = false))
        val barrier = AccountLockProbe.Barrier(owner.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(owner.actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
            } finally {
                accountProbe.current.set(null)
                barrier.release.countDown()
            }
        }
        employee(administrator.browser, administrator.csrf, owner.company)
        val next = invoke(owner.actor)
        assertTrue(next is Result.Success, next.toString())
    }
}
