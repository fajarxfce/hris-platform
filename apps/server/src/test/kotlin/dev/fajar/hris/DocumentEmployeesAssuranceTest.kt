package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.usecases.ListDocumentEmployees
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentEmployeesAssuranceTest : DocumentEmployeesApiFixture() {
    private fun directory(
        security: IdentitySecurityPolicy = IdentitySecurityPolicy(enforceMfa = false)
    ) = ListDocumentEmployees(people, companies, members, identities, transactions, security, clock)

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun proofExpiringDuringAccessWaitRequiresRenewal(exact: Boolean) {
        val reader = directoryFixture().owner
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor = enrolled(reader, security)
        val useCase = directory(security)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    useCase.execute(actor, "", null, 20, if (exact) reader.employee else null)
                }
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
        assertTrue(
            useCase.execute(
                actor.copy(mfaVerifiedAt = clock.instant()),
                "",
                null,
                20,
                if (exact) reader.employee else null,
            ) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["documents.read", "people.profile.read", "credentials", "membership"])
    fun pendingReadRechecksCurrentAuthority(change: String) {
        val reader = directoryFixture().owner
        val barrier = AccountLockProbe.Barrier(reader.actor.accountId, occurrence = 2)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    get(reader.browser, "${reader.path}/employees")
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credentials" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                reader.actor.accountId,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                reader.company,
                                reader.actor.accountId,
                            )
                    else ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                reader.company,
                                reader.actor.accountId,
                                change,
                            )
                }
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credentials") 401 else 403,
                    response.statusCode(),
                    response.body(),
                )
                assertFalse(response.body().contains("employeeNumber"))
                assertFalse(response.body().contains("Example employee"))
            } finally {
                accountProbe.current.set(null)
                barrier.release.countDown()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["documents.read", "people.profile.read", "documents.self.read"])
    fun originalRequestCannotExpandToNewGrants(original: String) {
        val reader = directoryFixture().owner
        val response =
            directory().execute(reader.actor.copy(permissions = setOf(original)), "", null, 20)
        assertEquals("access_denied", (response as? Result.Failed)?.failure?.code)
    }

    @Test
    fun interruptedReadReleasesGuardsAndDoesNotPoisonTheNextAcquisition() {
        val (administrator, reader) = directoryFixture()
        val useCase = directory()
        val barrier = AccountLockProbe.Barrier(reader.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { useCase.execute(reader.actor, "", null, 20) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
            } finally {
                accountProbe.current.set(null)
                barrier.release.countDown()
            }
        }
        employee(administrator.browser, administrator.csrf, reader.company)
        assertTrue(useCase.execute(reader.actor, "", null, 20) is Result.Success)
    }
}
