package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationSpaceMatrixDevice
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceKeyUploadTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun countsCreateNoIdentityAndSignedPublicationCanBeRecoveredWithoutChangingKeys() {
        val f = Fixture()
        val provider = f.keyProvider()
        success(f.upload(CollaborationMatrixKeyUpload(), provider))
        assertNull(f.publication())
        assertEquals(0, provider.reads.get())
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val request =
            CollaborationMatrixKeyUpload(
                keys.device,
                mapOf("signed_curve25519:one" to keys.signed()),
                mapOf("signed_curve25519:backup" to keys.signed(true)),
            )
        success(f.upload(request, provider))
        val published = requireNotNull(f.publication())
        assertEquals(1L, published.version)
        assertEquals(
            success(
                validateCollaborationDeviceKeys(keys.device, f.identity.userId, f.identity.deviceId)
            ),
            published.fingerprint,
        )
        assertEquals(2, provider.reads.get())
        success(f.upload(request, provider))
        success(f.upload(CollaborationMatrixKeyUpload(), provider))
        assertEquals(published, f.publication())
        assertEquals(1, f.journalCount("collaboration.space_device_keys_reserved"))
        assertEquals(1, f.journalCount("collaboration.space_device_keys_published"))
        assertEquals(0, f.leases())
    }

    @Test
    fun lostUploadRetainsReservationRejectsReplacementAndCountsNeverAcknowledgeIt() {
        val f = Fixture()
        val provider = f.keyProvider()
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val upload = CollaborationMatrixKeyUpload(keys.device)
        provider.afterUpload = {
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_lost_response"))
        }
        failure(f.upload(upload, provider), "fixture_lost_response")
        val reserved = requireNotNull(f.publication())
        assertEquals(0L, reserved.version)
        provider.afterUpload = { Result.Success(provider.counts) }
        success(f.upload(CollaborationMatrixKeyUpload(), provider))
        assertEquals(reserved, f.publication())
        val before = provider.uploads.get()
        failure(
            f.upload(
                CollaborationMatrixKeyUpload(
                    MatrixKeyFixture(f.identity.userId, f.identity.deviceId).device
                ),
                provider,
            ),
            "collaboration_space_device_keys_changed",
        )
        failure(
            f.upload(
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:pending" to keys.signed())
                ),
                provider,
            ),
            "collaboration_device_keys_required",
        )
        assertEquals(before, provider.uploads.get())
        success(f.upload(upload, provider))
        assertEquals(1L, f.publication()?.version)
        assertEquals(reserved, f.publication()?.copy(version = 0, publishedAt = null))
        success(
            f.upload(
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:next" to keys.signed())
                ),
                provider,
            )
        )
        assertEquals(0, f.leases())
    }

    @Test
    fun wrongProviderIdentityAndInvalidSignaturesCannotPublishOrReplaceReservedKeys() {
        val f = Fixture()
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val provider = f.keyProvider()
        provider.current = MatrixKeyFixture(f.identity.userId, f.identity.deviceId).device
        failure(
            f.upload(CollaborationMatrixKeyUpload(keys.device), provider),
            "collaboration_space_device_keys_changed",
        )
        assertEquals(0, provider.uploads.get())
        assertEquals(0L, f.publication()?.version)
        provider.current = null
        val bad = keys.signed().copy(signatures = emptyMap())
        assertInstanceOf(
            Result.Failed::class.java,
            f.upload(
                CollaborationMatrixKeyUpload(keys.device, mapOf("signed_curve25519:bad" to bad)),
                provider,
            ),
        )
        assertEquals(0, provider.uploads.get())
        provider.afterUpload = {
            provider.current = keys.device.copy(userId = "@foreign:matrix.example.test")
            Result.Success(provider.counts)
        }
        failure(
            f.upload(CollaborationMatrixKeyUpload(keys.device), provider),
            "collaboration_provider_invalid_response",
        )
        assertEquals(0L, f.publication()?.version)
        assertEquals(0, f.leases())
    }

    @Test
    fun currentDenialWinsOnEachProviderOutcomeWhileAnUncertainUploadRemainsOwned() {
        val f = Fixture()
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        for (succeeds in listOf(false, true)) {
            var denied = false
            val spaces =
                object : CollaborationSpaceRepository by f.base.spaces {
                    override fun current(): Result<UUID?> =
                        if (denied) Result.Success(null) else f.base.spaces.current()
                }
            val provider = f.keyProvider()
            provider.afterUpload = {
                denied = true
                if (succeeds) Result.Success(provider.counts)
                else Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_provider_failed"))
            }
            failure(
                f.command(providerKeys = provider, spaces = spaces)
                    .uploadKeys(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        CollaborationMatrixKeyUpload(keys.device),
                    ),
                "collaboration_access_denied",
            )
            assertEquals(0L, f.publication()?.version)
            assertEquals(0, f.leases())
        }
        val provider = f.keyProvider()
        provider.afterUpload = {
            f.s.owner.update("delete from native_sessions where id=?", f.s.parent)
            Result.Success(provider.counts)
        }
        failure(
            f.upload(CollaborationMatrixKeyUpload(keys.device), provider),
            "native_session_invalid",
        )
        assertEquals(0L, f.publication()?.version)
        assertEquals(0, f.leases())
    }

    @Test
    fun competingUploadsOfSameIdentityAcknowledgeOnceAndBothReturnTheirProviderResult() {
        val f = Fixture()
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val provider = f.keyProvider()
        val entered = CountDownLatch(2)
        provider.afterUpload = {
            entered.countDown()
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            Result.Success(provider.counts)
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                (0..1).map {
                    pool.submit<Result<CollaborationMatrixKeyCounts>> {
                        f.upload(CollaborationMatrixKeyUpload(keys.device), provider)
                    }
                }
            results.forEach { success(it.get(20, TimeUnit.SECONDS)) }
            assertEquals(1L, f.publication()?.version)
            assertEquals(1, f.journalCount("collaboration.space_device_keys_reserved"))
            assertEquals(1, f.journalCount("collaboration.space_device_keys_published"))
            assertEquals(0, f.leases())
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun journalAndPostWriteFailuresRollBackAcknowledgementWhileProviderStateCanRecover() {
        val f = Fixture()
        val provider = f.keyProvider()
        val signed = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val upload = CollaborationMatrixKeyUpload(signed.device)
        var reservation = true
        val journal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    f.base.journal.record(actor, change).flatMap {
                        if (
                            change.action ==
                                if (reservation) "collaboration.space_device_keys_reserved"
                                else "collaboration.space_device_keys_published"
                        )
                            Result.Failed(
                                Failure(FailureKind.UNAVAILABLE, "fixture_journal_failed")
                            )
                        else Result.Success(Unit)
                    }
            }
        failure(
            f.command(providerKeys = provider, journal = journal)
                .uploadKeys(f.tokens.accessToken, UUID.randomUUID(), upload),
            "fixture_journal_failed",
        )
        assertNull(f.publication())
        assertEquals(0, provider.uploads.get())
        reservation = false
        failure(
            f.command(providerKeys = provider, journal = journal)
                .uploadKeys(f.tokens.accessToken, UUID.randomUUID(), upload),
            "fixture_journal_failed",
        )
        assertEquals(0L, f.publication()?.version)
        assertEquals(0, f.journalCount("collaboration.space_device_keys_published"))
        for (cancel in listOf(false, true)) {
            val repository =
                object : CollaborationSpaceKeyRepository by f.keys {
                    override fun publish(
                        expected: CollaborationSpaceKeyPublication,
                        at: Instant,
                    ): Result<Unit> =
                        f.keys.publish(expected, at).flatMap {
                            if (cancel)
                                throw CancellationException("fixture publication cancellation")
                            Result.Failed(Failure(FailureKind.CONFLICT, "fixture_late_failure"))
                        }
                }
            val action = {
                f.command(providerKeys = provider, keys = repository)
                    .uploadKeys(f.tokens.accessToken, UUID.randomUUID(), upload)
            }
            if (cancel) assertThrows(CancellationException::class.java) { action() }
            else failure(action(), "fixture_late_failure")
            assertEquals(0L, f.publication()?.version)
            assertEquals(0, f.leases())
        }
        success(f.upload(upload, provider))
        assertEquals(1L, f.publication()?.version)
    }

    @Test
    fun callerMutationDuringProviderReadCannotAlterCapturedSignedKeys() {
        val f = Fixture()
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val device = keys.device.keys.toMutableMap()
        val oneTime = mutableMapOf("signed_curve25519:one" to keys.signed())
        val fallback = mutableMapOf("signed_curve25519:fallback" to keys.signed(true))
        val request =
            CollaborationMatrixKeyUpload(keys.device.copy(keys = device), oneTime, fallback)
        val provider = f.keyProvider()
        provider.onRead = { number ->
            if (number == 1) {
                device.clear()
                oneTime.clear()
                fallback.clear()
            }
        }
        success(f.upload(request, provider))
        val actual = provider.received.single()
        assertEquals(keys.device, actual.deviceKeys)
        assertEquals(1, actual.oneTimeKeys.size)
        assertEquals(1, actual.fallbackKeys.size)
        assertEquals(1L, f.publication()?.version)
    }

    @Test
    fun lateReleaseCannotEraseAReplacementRequestOrAcknowledgeItsPredecessorsUpload() {
        val f = Fixture()
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val provider = f.keyProvider()
        var replacement: CollaborationSpaceRequestLease? = null
        provider.afterUpload = {
            replacement =
                requireNotNull(
                    success(
                        f.s.tx {
                            f.requests.find(f.owner, 1, lock = true).flatMap { value ->
                                val old = requireNotNull(value)
                                f.requests.release(old).flatMap { released ->
                                    assertTrue(released)
                                    f.requests.claim(old.position, old.expiresAt)
                                }
                            }
                        }
                    )
                )
            Result.Success(provider.counts)
        }
        try {
            failure(
                f.upload(CollaborationMatrixKeyUpload(keys.device), provider),
                "collaboration_space_request_lost",
            )
            assertEquals(0L, f.publication()?.version)
            assertEquals(replacement, success(f.s.tx { f.requests.find(f.owner, 1) }))
            assertEquals(1, f.leases())
        } finally {
            replacement?.let { success(f.s.tx { f.requests.release(it) }) }
        }
    }

    @Test
    fun actualGroupProviderRetainsSignedIdentityAcrossUploadReplayAndServerRestart() {
        MatrixProviderFixture("10m").use { provider ->
            provider.start()
            val f = Fixture()
            val group =
                InternalCollaborationSpaceMatrixRepository(
                    dev.fajar.hris.collaboration.data.matrix.HmacMatrixJwtDataSource(
                        provider.config
                    ),
                    dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource(
                        provider.client,
                        provider.config,
                        Clock.systemUTC(),
                    ),
                    Clock.systemUTC(),
                )
            val at = Instant.now()
            val candidate =
                success(
                    group.login(
                        CollaborationSpaceMatrixLogin(
                            f.s.space,
                            f.s.account,
                            f.s.device,
                            null,
                            null,
                            at,
                            at.plusSeconds(30),
                        )
                    )
                )
            f.rotateProvider(candidate.accessToken, candidate.expiresAt)
            fun keyRepository() =
                InternalCollaborationMatrixKeyRepository(
                    dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource(
                        provider.client,
                        provider.config,
                        Clock.systemUTC(),
                    )
                )
            val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
            val request =
                CollaborationMatrixKeyUpload(
                    keys.device,
                    mapOf("signed_curve25519:one" to keys.signed()),
                    mapOf("signed_curve25519:fallback" to keys.signed(true)),
                )
            success(f.upload(request, keyRepository()))
            val before = requireNotNull(f.publication())
            assertEquals(1L, before.version)
            provider.restart()
            success(f.upload(request, keyRepository()))
            assertEquals(before, f.publication())
            assertEquals(
                keys.device,
                success(
                    keyRepository()
                        .ownDeviceKeys(
                            f.identity.userId,
                            f.identity.deviceId,
                            candidate.accessToken,
                            Instant.now().plusSeconds(20),
                        )
                ),
            )
            failure(
                f.upload(
                    CollaborationMatrixKeyUpload(
                        MatrixKeyFixture(f.identity.userId, f.identity.deviceId).device
                    ),
                    keyRepository(),
                ),
                "collaboration_space_device_keys_changed",
            )
            assertEquals(0, f.leases())
        }
    }

    @Test
    fun alreadyAcknowledgedMetadataCannotChangeDuringARepeatedUpload() {
        val f = Fixture()
        val provider = f.keyProvider()
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val request = CollaborationMatrixKeyUpload(keys.device)
        success(f.upload(request, provider))
        val original = requireNotNull(f.publication())
        var changed = false
        val repository =
            object : CollaborationSpaceKeyRepository by f.keys {
                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                ): Result<CollaborationSpaceKeyPublication?> =
                    f.keys.find(spaceId, accountId, deviceId).map {
                        if (changed)
                            it?.copy(publishedAt = requireNotNull(it.publishedAt).plusNanos(1000))
                        else it
                    }
            }
        provider.afterUpload = {
            changed = true
            Result.Success(provider.counts)
        }
        failure(
            f.command(providerKeys = provider, keys = repository)
                .uploadKeys(f.tokens.accessToken, UUID.randomUUID(), request),
            "collaboration_space_device_keys_changed",
        )
        assertEquals(original, f.publication())
        assertEquals(0, f.leases())
    }

    @Test
    fun providerAndNativeGenerationChangesDuringKeyReadbackWithholdAcknowledgement() {
        val f = Fixture()
        val keys = MatrixKeyFixture(f.identity.userId, f.identity.deviceId)
        val request = CollaborationMatrixKeyUpload(keys.device)
        for (native in listOf(false, true)) {
            val provider = f.keyProvider()
            provider.onRead = { number ->
                if (number == 2) {
                    if (native)
                        f.s.owner.update(
                            "update native_sessions set version=version+1 where id=?",
                            f.s.parent,
                        )
                    else f.rotateProvider()
                }
            }
            failure(f.upload(request, provider), "collaboration_admission_changed")
            assertEquals(0L, f.publication()?.version)
            assertEquals(0, f.leases())
        }
        success(f.upload(request, f.keyProvider()))
        assertEquals(1L, f.publication()?.version)
    }

    private inner class Fixture {
        val base = SpaceGatewayBootstrapFixture(postgres)
        val s = base.storage
        val owner = CollaborationSpaceRequestOwner(s.space, s.account, s.device, s.parent)
        val requests =
            StoredCollaborationSpaceRequestRepository(
                PostgresCollaborationSpaceRequestDataSource(base.sql)
            )
        val providers =
            EncryptedCollaborationSpaceProviderSessionRepository(
                PostgresCollaborationSpaceProviderSessionDataSource(base.sql),
                JceIdentityTokenDataSource(
                    parseIdentityKeyring(
                        "fixture",
                        "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 63 }),
                    )
                ),
            )
        val keys =
            StoredCollaborationSpaceKeyRepository(PostgresCollaborationSpaceKeyDataSource(base.sql))
        val calls = AtomicInteger()
        val identity =
            CollaborationMatrixIdentity(
                s.user,
                "HRISG_" + s.device.toString().replace("-", ""),
                false,
            )
        val tokens: CollaborationSpaceGatewayTokens

        init {
            Flyway.configure().dataSource(s.source).target("128").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on collaboration_space_request_leases,collaboration_space_key_publications to space_gateway_fixture,space_gateway_worker"
            )
            rotateProvider()
            base.clock.at = Instant.now()
            tokens = success(base.bootstrap(command = base.command(providers = providers)))
        }

        fun rotateProvider(
            token: String = "fictional-provider-token",
            expiresAt: Instant = Instant.now().plusSeconds(300),
        ) {
            success(
                s.tx {
                    providers.metadata(s.space, s.account, s.device).flatMap {
                        val before = requireNotNull(it)
                        providers.rotate(
                            CollaborationSpaceProviderSession(
                                before.copy(
                                    generation = before.generation + 1,
                                    updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                                    expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS),
                                ),
                                token,
                            ),
                            before.generation,
                        )
                    }
                }
            )
        }

        fun matrix(
            action: () -> Result<CollaborationMatrixIdentity> = { Result.Success(identity) }
        ) =
            object : CollaborationMatrixRepository {
                override fun login(
                    request: CollaborationMatrixLogin
                ): Result<CollaborationMatrixCredential> =
                    error("Group access must not use company login")

                override fun logoutDevice(accessToken: String): Result<Unit> =
                    error("Group access must not logout")

                override fun whoAmI(
                    accessToken: String,
                    deadline: Instant,
                ): Result<CollaborationMatrixIdentity> {
                    calls.incrementAndGet()
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
                    assertEquals("fictional-provider-token", accessToken)
                    assertTrue(leases() in 1..7)
                    assertTrue(deadline <= Instant.now().plusSeconds(30))
                    return action()
                }
            }

        fun command(
            matrix: CollaborationMatrixRepository = matrix(),
            providers: CollaborationSpaceProviderSessionRepository = this.providers,
            spaces: CollaborationSpaceRepository = base.spaces,
            policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 5, 5),
            clock: Clock = Clock.systemUTC(),
            keys: CollaborationSpaceKeyRepository = this.keys,
            providerKeys: CollaborationMatrixKeyRepository = keyProvider(),
            journal: ChangeJournalRepository = base.journal,
        ) =
            AccessNativeCollaborationSpaceMatrixDevice(
                s.repo,
                requests,
                base.registry,
                providers,
                matrix,
                base.selections,
                base.legacy,
                base.completions,
                base.provisioning,
                spaces,
                base.sessions,
                base.identities,
                s.transactions,
                IdentitySecurityPolicy(),
                policy,
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                clock,
                keys,
                providerKeys,
                journal,
                StoredCollaborationSpaceRecipientRepository(
                    PostgresCollaborationSpaceRecipientDataSource(base.sql)
                ),
                DisabledCollaborationMatrixToDeviceRepository(),
                StoredCollaborationSpaceRoomRepository(
                    PostgresCollaborationSpaceRoomDataSource(base.sql)
                ),
                DisabledCollaborationMatrixRoomMessageRepository(),
                DisabledCollaborationSpaceMatrixRoomHistoryRepository(),
                StoredCollaborationSpaceConversationRepository(
                    PostgresCollaborationSpaceConversationDataSource(base.sql)
                ),
                EncryptedCollaborationSpaceSyncCursorRepository(collaborationCursorFixture()),
                DisabledCollaborationSpaceMatrixSyncRepository(),
                DisabledCollaborationSpaceMatrixRoomAccessRepository(),
            )

        fun upload(
            request: CollaborationMatrixKeyUpload,
            provider: CollaborationMatrixKeyRepository,
        ) =
            command(providerKeys = provider)
                .uploadKeys(tokens.accessToken, UUID.randomUUID(), request)

        fun publication() = success(s.tx { keys.find(s.space, s.account, s.device) })

        fun journalCount(action: String) =
            s.owner.queryForObject(
                "select count(*) from audit_entries where action=? and resource_id=?",
                Int::class.java,
                action,
                s.device,
            )!!

        fun keyProvider() = KeyProvider(identity) { assertTrue(leases() in 1..7) }

        fun access(
            token: String = tokens.accessToken,
            command: AccessNativeCollaborationSpaceMatrixDevice = command(),
        ) = command.identity(token, UUID.randomUUID())

        fun leases() =
            s.owner.queryForObject(
                "select count(*) from collaboration_space_request_leases",
                Int::class.java,
            )!!

        fun provenance() =
            listOf(
                    "collaboration_space_devices",
                    "collaboration_space_provider_sessions",
                    "collaboration_space_gateway_sessions",
                    "collaboration_legacy_runtime",
                )
                .associateWith { s.owner.queryForList("select * from $it") }
    }

    private class KeyProvider(
        val identity: CollaborationMatrixIdentity,
        val validateLease: () -> Unit,
    ) : CollaborationMatrixKeyRepository {
        @Volatile var current: CollaborationMatrixDeviceKeys? = null
        val reads = AtomicInteger()
        val uploads = AtomicInteger()
        val received = ConcurrentLinkedQueue<CollaborationMatrixKeyUpload>()
        var onRead: (Int) -> Unit = {}
        val counts = CollaborationMatrixKeyCounts(mapOf("signed_curve25519" to 1))
        var afterUpload: () -> Result<CollaborationMatrixKeyCounts> = { Result.Success(counts) }

        private fun validate(token: String, deadline: Instant) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            assertEquals("fictional-provider-token", token)
            assertTrue(deadline <= Instant.now().plusSeconds(30))
            validateLease()
        }

        override fun ownDeviceKeys(
            userId: String,
            deviceId: String,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixDeviceKeys?> {
            validate(token, deadline)
            assertEquals(identity.userId, userId)
            assertEquals(identity.deviceId, deviceId)
            onRead(reads.incrementAndGet())
            return Result.Success(current)
        }

        override fun upload(
            request: CollaborationMatrixKeyUpload,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixKeyCounts> {
            validate(token, deadline)
            uploads.incrementAndGet()
            received.add(request)
            request.deviceKeys?.let { current = it }
            return afterUpload()
        }

        override fun claimKeys(
            request: CollaborationMatrixKeyClaim,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixKeyClaimResult> = error("No group recipient authority")

        override fun deviceKeys(
            request: CollaborationMatrixKeyQuery,
            token: String,
            deadline: Instant,
        ): Result<CollaborationMatrixKeyQueryResult> = error("No group recipient authority")
    }

    private class OffsetClock(var seconds: Long = 0) : Clock() {
        override fun instant(): Instant = Instant.now().plusSeconds(seconds)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            (result as? Result.Failed)?.failure?.code ?: "unexpected result"
        }
        return (result as Result.Success).value
    }

    private fun failure(result: Result<*>, code: String) {
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(code, (result as Result.Failed).failure.code)
    }
}
