package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRepository
import dev.fajar.hris.collaboration.domain.usecases.ListNativeCollaborationSpaceContacts
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceAdmissionTest {
    private val now = Instant.parse("2026-11-03T18:00:00Z")
    private val account =
        Account(
            UUID.randomUUID(),
            "employee@example.test",
            "Employee",
            true,
            false,
            3,
            securityVersion = 3,
        )
    private val actor =
        Actor(
            account.id,
            null,
            emptySet(),
            now.minusSeconds(120),
            UUID.randomUUID(),
            credentialVersion = 3,
        )
    private val access =
        AccountAccess(account, emptySet(), true, true, setOf(COLLABORATION_CHAT_PERMISSION))
    private val parent =
        NativeSession(
            UUID.randomUUID(),
            account.id,
            "Fictional device",
            now.minusSeconds(60),
            actor.authenticatedAt,
            null,
            3,
            now.plusSeconds(3600),
            now.plusSeconds(900),
            now.minusSeconds(30),
            null,
            2,
            UUID.randomUUID(),
            null,
        )
    private val space = UUID.randomUUID()
    private val client = ClientVersion(ClientPlatform.ANDROID, 28)
    private val policy = CollaborationSpacePolicy(true, 27, 9)
    private val security = IdentitySecurityPolicy()

    private fun admit(
        request: Actor = actor,
        live: AccountAccess? = access,
        session: NativeSession? = parent,
        currentSpace: UUID? = space,
        version: ClientVersion? = client,
        availability: CollaborationSpacePolicy = policy,
        at: Instant = now,
    ) =
        admitCollaborationSpaceSession(
            request,
            live,
            session,
            parent.id,
            parent.version,
            currentSpace,
            version,
            availability,
            security,
            at,
        )

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    private fun failure(result: Result<*>, code: String? = null) {
        assertInstanceOf(Result.Failed::class.java, result)
        if (code != null) assertEquals(code, (result as Result.Failed).failure.code)
    }

    @Test
    fun ordinaryEmployeeNeedsNoPlatformGrantAndKeepsEarlierAuthenticationTime() {
        val observation = success(admit())
        assertEquals(space, observation.spaceId)
        assertEquals(account.id, observation.accountId)
        assertEquals(parent.id, observation.nativeSessionId)
        assertEquals(2, observation.nativeSessionVersion)
        assertEquals(3, observation.credentialVersion)
        assertEquals(client, observation.client)
        assertEquals(now.plusSeconds(60), observation.expiresAt)
        assertTrue(actor.permissions.isEmpty())
        assertTrue(parent.authenticatedAt < parent.createdAt)
    }

    @Test
    fun companyScopeAndGlobalPermissionsCannotReplaceGroupEligibility() {
        failure(
            admit(request = actor.copy(companyId = UUID.randomUUID())),
            "platform_scope_required",
        )
        failure(admit(currentSpace = null), "collaboration_access_denied")
        failure(
            admit(
                request = actor.copy(permissions = setOf("identity.manage")),
                currentSpace = null,
            ),
            "collaboration_access_denied",
        )
        failure(admit(live = null), "session_revoked")
        failure(
            admit(live = access.copy(account = account.copy(active = false))),
            "session_revoked",
        )
        failure(
            admit(live = access.copy(account = account.copy(id = UUID.randomUUID()))),
            "session_revoked",
        )
        failure(
            admit(live = access.copy(account = account.copy(securityVersion = 4))),
            "session_revoked",
        )
    }

    @Test
    fun nativeIdentityProofRotationRevocationAndChronologyAreExact() {
        val invalid =
            listOf(
                null,
                parent.copy(id = UUID.randomUUID()),
                parent.copy(accountId = UUID.randomUUID()),
                parent.copy(credentialVersion = 4),
                parent.copy(version = 3),
                parent.copy(revokedAt = now),
                parent.copy(authenticatedAt = now.minusSeconds(1)),
                parent.copy(mfaVerifiedAt = now),
                parent.copy(createdAt = now.plusSeconds(1)),
                parent.copy(rotatedAt = now.plusSeconds(1)),
                parent.copy(rotatedAt = parent.createdAt.minusSeconds(1)),
                parent.copy(accessExpiresAt = parent.expiresAt.plusSeconds(1)),
                parent.copy(accessExpiresAt = now),
                parent.copy(expiresAt = now, accessExpiresAt = now),
            )
        for (session in invalid) failure(admit(session = session), "native_session_invalid")
        failure(admit(request = actor.copy(credentialVersion = null)), "native_session_invalid")
        failure(
            admit(
                request = actor.copy(authenticatedAt = now.plusSeconds(1)),
                session = parent.copy(authenticatedAt = now.plusSeconds(1)),
            ),
            "native_session_invalid",
        )
    }

    @Test
    fun currentSecurityUnionRequiresMfaAndClipsItsExpiry() {
        failure(
            admit(live = access.copy(securityPermissions = setOf("identity.manage"))),
            "mfa_setup_required",
        )
        val enrolled = access.copy(account = account.copy(mfaConfigured = true))
        failure(admit(live = enrolled), "mfa_required")
        val verified = now.minus(security.maximumMfaAge).plusSeconds(1)
        val request = actor.copy(mfaVerifiedAt = verified)
        val session = parent.copy(mfaVerifiedAt = verified)
        assertEquals(now.plusSeconds(1), success(admit(request, enrolled, session)).expiresAt)
        failure(admit(request, enrolled, session, at = now.plusSeconds(1)), "mfa_required")
        failure(
            admit(
                request = actor.copy(mfaVerifiedAt = now.plusSeconds(1)),
                live = enrolled,
                session = parent.copy(mfaVerifiedAt = now.plusSeconds(1)),
            ),
            "mfa_required",
        )
    }

    @Test
    fun organizationAvailabilityHasBoundedIndependentNativeMinimumBuilds() {
        failure(admit(availability = policy.copy(enabled = false)), "collaboration_unavailable")
        for (version in
            listOf(
                null,
                ClientVersion(ClientPlatform.WEB, 28),
                client.copy(build = 0),
                client.copy(build = 1000000000),
            )) failure(admit(version = version), "invalid_client_version")
        failure(admit(version = client.copy(build = 26)), "client_update_required")
        failure(admit(version = ClientVersion(ClientPlatform.IOS, 8)), "client_update_required")
        assertEquals(
            ClientPlatform.IOS,
            success(admit(version = ClientVersion(ClientPlatform.IOS, 9))).client.platform,
        )
        assertThrows(IllegalArgumentException::class.java) { policy.copy(minimumAndroidBuild = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            policy.copy(minimumIosBuild = 1000000000)
        }
    }

    @Test
    fun shortAndHardNativeExpiryClipTheObservationWithoutClockTolerance() {
        assertEquals(
            now.plusSeconds(2),
            success(admit(session = parent.copy(accessExpiresAt = now.plusSeconds(2)))).expiresAt,
        )
        assertEquals(
            now.plusSeconds(1),
            success(
                    admit(
                        session =
                            parent.copy(
                                accessExpiresAt = now.plusSeconds(1),
                                expiresAt = now.plusSeconds(1),
                            )
                    )
                )
                .expiresAt,
        )
        failure(admit(at = parent.accessExpiresAt), "native_session_invalid")
    }

    private inner class Fixture {
        var at = now
        var live: AccountAccess? = access
        var session: NativeSession? = parent
        var eligible: UUID? = space
        val calls = mutableListOf<String>()
        var beforePage: () -> Unit = {}
        var accountLock: () -> Unit = {}
        var pageResult: Result<Page<CollaborationSpaceContact>> =
            Result.Success(
                Page(
                    listOf(
                        CollaborationSpaceContact(
                            UUID.randomUUID(),
                            "Peer",
                            listOf(CollaborationContactCompany(UUID.randomUUID(), "Other company")),
                            false,
                        )
                    ),
                    null,
                )
            )
        var endedWithFailure = false
        private val identities =
            proxy<IdentityRepository> { name, args ->
                when (name) {
                    "lockAccount" -> {
                        assertEquals(account.id, args[0])
                        assertEquals(true, args[1])
                        calls += "account"
                        accountLock()
                        Result.Success(Unit)
                    }
                    "access" -> {
                        assertEquals(account.id, args[0])
                        assertNull(args[1])
                        calls += "access"
                        Result.Success(live)
                    }
                    else -> error("Unexpected identity operation: $name")
                }
            }
        private val sessions =
            proxy<NativeSessionRepository> { name, args ->
                assertEquals("lock", name)
                assertEquals(parent.id, args[0])
                assertEquals(account.id, args[1])
                calls += "session"
                Result.Success(session)
            }
        private val spaces =
            object : CollaborationSpaceRepository {
                override fun current(): Result<UUID?> {
                    calls += "space"
                    return Result.Success(eligible)
                }

                override fun contacts(
                    spaceId: UUID,
                    excludedAccountId: UUID,
                    query: String,
                    after: UUID?,
                    limit: Int,
                ): Result<Page<CollaborationSpaceContact>> {
                    assertEquals(space, spaceId)
                    assertEquals(account.id, excludedAccountId)
                    assertEquals("Peer", query)
                    assertNull(after)
                    assertEquals(30, limit)
                    calls += "page"
                    beforePage()
                    return pageResult
                }
            }
        private val transactions =
            object : TransactionRunner {
                override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> {
                    assertNull(actor.companyId)
                    calls += "begin"
                    return operation().also {
                        endedWithFailure = it is Result.Failed
                        calls += "end"
                    }
                }
            }
        private val clock =
            object : Clock() {
                override fun instant() = at

                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId): Clock = this
            }
        private val useCase =
            ListNativeCollaborationSpaceContacts(
                spaces,
                sessions,
                identities,
                transactions,
                policy,
                security,
                clock,
            )

        fun execute(
            request: Actor = actor,
            target: UUID = space,
            query: String = " Peer ",
            limit: Int = 30,
        ) = useCase.execute(request, parent.id, parent.version, target, client, query, null, limit)
    }

    @Test
    fun directoryReadOwnsAccountSessionTransactionAndRechecksCurrentScopeAtCompletion() {
        val fixture = Fixture()
        assertEquals(success(fixture.pageResult), success(fixture.execute()))
        assertEquals(
            listOf(
                "begin",
                "account",
                "session",
                "access",
                "space",
                "page",
                "access",
                "space",
                "end",
            ),
            fixture.calls,
        )
        assertFalse(fixture.endedWithFailure)
    }

    @Test
    fun invalidInputAndWrongSpaceNeverAcquireContactRows() {
        for (run in
            listOf<(Fixture) -> Result<*>>(
                { it.execute(limit = 51) },
                { it.execute(query = "a".repeat(81)) },
                { it.execute(query = "Peer\n") },
                { it.execute(request = actor.copy(companyId = UUID.randomUUID())) },
            )) {
            val fixture = Fixture()
            failure(run(fixture))
            assertTrue(fixture.calls.isEmpty())
        }
        val fixture = Fixture()
        failure(fixture.execute(target = UUID.randomUUID()), "collaboration_access_denied")
        assertFalse("page" in fixture.calls)
        assertTrue(fixture.endedWithFailure)
    }

    @Test
    fun lateOffboardingSecurityChangeAndExpiryWithholdAcquiredContacts() {
        for (change in
            listOf<(Fixture) -> Unit>(
                { it.eligible = null },
                { it.live = access.copy(account = account.copy(securityVersion = 4)) },
                { it.live = access.copy(securityPermissions = setOf("identity.manage")) },
                { it.at = parent.accessExpiresAt },
            )) {
            val fixture = Fixture()
            fixture.beforePage = { change(fixture) }
            failure(fixture.execute())
            assertTrue(fixture.endedWithFailure)
            assertEquals(1, fixture.calls.count { it == "page" })
        }
    }

    @Test
    fun failureAndInterruptionNeverTurnIntoAnEmptyPageOrRetry() {
        val failed = Fixture()
        failed.pageResult = Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_unavailable"))
        failure(failed.execute(), "database_unavailable")
        assertTrue(failed.endedWithFailure)
        assertEquals(1, failed.calls.count { it == "page" })
        val interrupted = Fixture()
        interrupted.beforePage = { throw InterruptedException() }
        assertThrows(InterruptedException::class.java) { interrupted.execute() }
        assertEquals(1, interrupted.calls.count { it == "page" })
    }

    @Test
    fun lockWaitExpiryAndForeignParentAreDeniedBeforeContactAcquisition() {
        val expired = Fixture()
        expired.accountLock = { expired.at = parent.accessExpiresAt }
        failure(expired.execute(), "native_session_invalid")
        assertFalse("page" in expired.calls)
        val replaced = Fixture()
        replaced.session = parent.copy(id = UUID.randomUUID())
        failure(replaced.execute(), "native_session_invalid")
        assertFalse("page" in replaced.calls)
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args
            ->
            call(method.name, args ?: emptyArray())
        } as T
}
