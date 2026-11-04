package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/** Actual root/worker RLS and recovery; no queue record grants background/content authority. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollaborationSpacePushQueueTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    private data class Identity(
        val account: UUID = UUID.randomUUID(),
        val device: UUID = UUID.randomUUID(),
        val parent: UUID = UUID.randomUUID(),
    )

    private val cohort = List(9) { Identity() }
    private lateinit var f: SpaceKeyExchangeFixture
    private lateinit var source: PostgresCollaborationSpacePushQueueDataSource
    private lateinit var workerSource: PostgresCollaborationSpacePushQueueDataSource
    private lateinit var repo: CollaborationSpacePushQueueRepository
    private lateinit var workerRepo: CollaborationSpacePushQueueRepository
    private lateinit var own: CollaborationSpacePushBinding
    private lateinit var peer: CollaborationSpacePushBinding
    private lateinit var bindings: List<CollaborationSpacePushBinding>

    @BeforeAll
    fun migrateRetainedBindingsWithoutRewritingTheirCapabilities() {
        f = SpaceKeyExchangeFixture(postgres) { s -> cohort.forEach { seed(s, it) } }
        Flyway.configure().dataSource(f.s.source).target("136").load().migrate()
        f.s.owner.execute(
            "grant select,insert,update,delete on collaboration_space_push_bindings to space_gateway_fixture,space_gateway_worker"
        )
        own = reserve(Identity(f.s.account, f.s.device, f.s.parent))
        peer = reserve(Identity(f.peer, f.peerDevice, f.peerParent))
        bindings = cohort.map(::reserve)
        val before =
            f.s.owner.queryForList(
                "select * from collaboration_space_push_bindings order by space_id,device_id"
            )
        Flyway.configure().dataSource(f.s.source).target("137").load().migrate()
        assertEquals(
            before,
            f.s.owner.queryForList(
                "select * from collaboration_space_push_bindings order by space_id,device_id"
            ),
        )
        f.s.owner.execute(
            "grant select,insert,update,delete on collaboration_space_push_hints to space_gateway_fixture,space_gateway_worker"
        )
        source = PostgresCollaborationSpacePushQueueDataSource(f.base.sql)
        workerSource =
            PostgresCollaborationSpacePushQueueDataSource(
                DSL.using(TransactionAwareDataSourceProxy(f.s.workerSource), SQLDialect.POSTGRES)
            )
        repo = StoredCollaborationSpacePushQueueRepository(source)
        workerRepo = StoredCollaborationSpacePushQueueRepository(workerSource)
    }

    @AfterEach
    fun closeOwnedQueueFixture() {
        f.s.owner.execute("truncate collaboration_space_push_hints")
    }

    @Test
    fun lifetimeBoundsUseElapsedSecondsAcrossDatabaseSessionDaylightSavingChanges() {
        val created = "2026-03-08T00:30:00-05:00"
        fun insert(binding: CollaborationSpacePushBinding, seconds: Int) =
            f.s.tx(f.s.actor.copy(accountId = binding.accountId)) {
                safeDatabaseCall {
                    f.s.runtime.execute("set local time zone 'America/New_York'")
                    f.s.runtime.update(
                        "insert into collaboration_space_push_hints(space_id,device_id,account_id,native_session_id,revision,created_at,last_hint_at,expires_at,available_at) values(?,?,?,?,1,?::timestamptz,?::timestamptz,?::timestamptz+make_interval(secs=>?),?::timestamptz)",
                        binding.spaceId,
                        binding.deviceId,
                        binding.accountId,
                        binding.nativeSessionId,
                        created,
                        created,
                        created,
                        seconds,
                        created,
                    )
                }
            }
        assertEquals(1, success(insert(own, 86400)))
        assertEquals(
            Duration.ofSeconds(86400),
            Duration.between(head().lastHintAt, head().expiresAt),
        )
        assertInstanceOf(Result.Failed::class.java, insert(bindings.first(), 86401))
    }

    @Test
    fun duplicateEventsAndQuietHintsCoalesceWhileTheRecentWindowRemainsBounded() {
        assertTrue(success(append(event(1))))
        val original = head()
        assertFalse(success(append(event(1))))
        assertFalse(success(append(null)))
        assertEquals(original, head())
        for (i in 2..70) assertTrue(success(append(event(i))))
        assertEquals(70L, head().revision)
        assertEquals(64, head().recentEventCount)
        assertEquals(event(70), head().event)
        assertEquals(original.createdAt, head().createdAt)
        assertFalse(success(append(event(70))))
        assertTrue(success(append(event(1))))
        assertEquals(64, head().recentEventCount)
        val raw =
            f.s.owner.queryForObject(
                "select array_to_string(recent_event_hashes,',') from collaboration_space_push_hints",
                String::class.java,
            )!!
        assertFalse(raw.contains("fictional"))
        assertEquals(64, raw.split(',').toSet().size)
        assertTrue(raw.split(',').all { it.matches(Regex("[a-f0-9]{64}")) })
    }

    @Test
    fun rootOwnershipCannotBorrowAnotherAccountOrCompanyContextOrWorkerPermission() {
        success(append(event(1)))
        for (actor in
            listOf(f.s.actor.copy(accountId = f.peer), f.s.actor.copy(companyId = f.s.company))) {
            assertNull(
                success(f.s.tx(actor) { repo.find(own.spaceId, own.accountId, own.deviceId, true) })
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.s.tx(actor) { repo.append(own, event(2), 300) },
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.tx { repo.claim(UUID.randomUUID(), 1, 30, 8) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.tx { repo.append(own.copy(nativeSessionId = UUID.randomUUID()), event(2), 300) },
        )
        val lease = claim().single().lease!!
        assertInstanceOf(Result.Failed::class.java, f.s.tx { repo.finish(lease) })
        assertEquals(lease, head().lease)
        assertTrue(success(worker { workerRepo.finish(lease) }))
        assertEquals(1L, head().deliveredRevision)
    }

    @Test
    fun simultaneousCallbacksDeduplicateIdenticalEventsAndRetainDistinctRevisions() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val entered = CountDownLatch(4)
            val release = CountDownLatch(1)
            val duplicates =
                List(4) {
                    pool.submit<Boolean> {
                        entered.countDown()
                        assertTrue(release.await(10, TimeUnit.SECONDS))
                        success(append(event(1)))
                    }
                }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(1, duplicates.count { it.get(10, TimeUnit.SECONDS) })
            val distinct = (2..17).map { i -> pool.submit<Boolean> { success(append(event(i))) } }
            assertTrue(distinct.all { it.get(10, TimeUnit.SECONDS) })
            assertEquals(17L, head().revision)
            assertEquals(17, head().recentEventCount)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun aNewCallbackSurvivesAnOlderAcknowledgementAndAForeignLeaseCannotConsumeIt() {
        success(append(event(1)))
        val old = claim().single().lease!!
        success(append(event(2)))
        assertTrue(success(worker { workerRepo.finish(old) }))
        assertEquals(2L, head().revision)
        assertEquals(1L, head().deliveredRevision)
        val next = claim().single().lease!!
        assertFalse(success(worker { workerRepo.finish(old) }))
        assertFalse(success(worker { workerRepo.finish(next.copy(token = UUID.randomUUID())) }))
        assertEquals(next, head().lease)
        assertTrue(success(worker { workerRepo.finish(next) }))
        assertEquals(2L, head().deliveredRevision)
    }

    @Test
    fun shorteningHeadLifetimeKeepsTheInFlightLeaseAndOnlyAcknowledgesItsCapturedRevision() {
        success(append(event(1)))
        val old = claim().single().lease!!
        assertTrue(success(append(event(2), lifetime = 1)))
        val current = head()
        assertEquals(old, current.lease)
        assertTrue(current.expiresAt < old.expiresAt)
        assertTrue(success(worker { workerRepo.finish(old) }))
        assertEquals(1L, head().deliveredRevision)
        assertEquals(2L, head().revision)
    }

    @Test
    fun deferredWorkPreservesNewerHintsAndAttemptLimitsRequireANewRevision() {
        success(append(null))
        repeat(2) {
            val lease = claim(maximumAttempts = 2).single().lease!!
            assertTrue(success(worker { workerRepo.defer(lease, Instant.now()) }))
        }
        assertTrue(claim(maximumAttempts = 2).isEmpty())
        assertTrue(success(append(null)))
        val fresh = claim(maximumAttempts = 2).single()
        assertEquals(1, fresh.attempts)
        assertTrue(success(append(event(3))))
        val before = Instant.now()
        assertTrue(
            success(worker { workerRepo.defer(fresh.lease!!, Instant.now().plusSeconds(120)) })
        )
        assertTrue(head().availableAt >= before.truncatedTo(ChronoUnit.MICROS))
        assertTrue(head().availableAt <= Instant.now())
        assertEquals(3L, head().revision)
        assertEquals(0L, head().deliveredRevision)
        assertEquals(3L, claim().single().lease!!.revision)
    }

    @Test
    fun globalGroupCapacityIsEightAndAnExactCompletionMakesOneSlotAvailable() {
        bindings.forEach { success(append(event(1), it)) }
        val first = claim()
        assertEquals(8, first.size)
        assertEquals(8, first.map { it.deviceId }.toSet().size)
        assertTrue(claim().isEmpty())
        assertTrue(success(worker { workerRepo.finish(first.first().lease!!) }))
        val next = claim()
        assertEquals(1, next.size)
        assertFalse(next.single().deviceId in first.map { it.deviceId })
        assertInstanceOf(
            Result.Failed::class.java,
            worker { workerRepo.claim(UUID(0, 0), 1, 30, 8) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            worker { workerRepo.claim(UUID.randomUUID(), 9, 30, 8) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            worker { safeDatabaseCall { workerSource.claim(UUID(0, 0), 1, 30, 8) } },
        )
    }

    @Test
    fun failedAppendAndCompletionReadbacksRollbackActualWrites() {
        var hide = false
        val fault =
            object : CollaborationSpacePushQueueDataSource by source {
                override fun append(
                    spaceId: UUID,
                    deviceId: UUID,
                    accountId: UUID,
                    parentId: UUID,
                    roomId: String?,
                    eventId: String?,
                    eventHash: String?,
                    lifetimeSeconds: Int,
                ): Boolean =
                    source
                        .append(
                            spaceId,
                            deviceId,
                            accountId,
                            parentId,
                            roomId,
                            eventId,
                            eventHash,
                            lifetimeSeconds,
                        )
                        .also { hide = true }

                override fun find(spaceId: UUID, accountId: UUID, deviceId: UUID, lock: Boolean) =
                    if (hide) null else source.find(spaceId, accountId, deviceId, lock)
            }
        val broken = StoredCollaborationSpacePushQueueRepository(fault)
        assertInstanceOf(Result.Failed::class.java, f.s.tx { broken.append(own, event(1), 300) })
        assertNull(success(f.s.tx { repo.find(own.spaceId, own.accountId, own.deviceId) }))
        success(append(event(1)))
        val lease = claim().single().lease!!
        val before = head()
        val completionFault =
            object : CollaborationSpacePushQueueDataSource by workerSource {
                override fun find(spaceId: UUID, accountId: UUID, deviceId: UUID, lock: Boolean) =
                    null
            }
        val brokenWorker = StoredCollaborationSpacePushQueueRepository(completionFault)
        assertInstanceOf(Result.Failed::class.java, worker { brokenWorker.finish(lease) })
        assertEquals(before, head())
        assertTrue(success(worker { workerRepo.finish(lease) }))
    }

    @Test
    fun expiredLeaseCannotAcknowledgeOrDeferItsSuccessor() {
        success(append(event(1)))
        val old = success(worker { workerRepo.claim(UUID.randomUUID(), 1, 1, 8) }).single().lease!!
        val remaining = Duration.between(Instant.now(), old.expiresAt).toMillis()
        if (remaining >= 0) Thread.sleep(minOf(remaining + 100, 1200))
        assertFalse(success(worker { workerRepo.finish(old) }))
        val next = claim().single().lease!!
        assertNotEquals(old.token, next.token)
        assertFalse(success(worker { workerRepo.defer(old, Instant.now()) }))
        assertEquals(next, head().lease)
        assertTrue(success(worker { workerRepo.finish(next) }))
    }

    @Test
    fun acquisitionCancellationAndInterruptionPropagateWithoutChangingRetainedState() {
        success(append(event(1)))
        val before = head()
        var interrupted = false
        val unavailable =
            object : CollaborationSpacePushQueueDataSource by source {
                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                    lock: Boolean,
                ): dev.fajar.hris.schema.tables.records.CollaborationSpacePushHintsRecord? {
                    if (interrupted) throw InterruptedException("fictional queue interruption")
                    throw CancellationException("fictional queue cancellation")
                }
            }
        val broken = StoredCollaborationSpacePushQueueRepository(unavailable)
        assertThrows(CancellationException::class.java) {
            f.s.tx { broken.find(own.spaceId, own.accountId, own.deviceId) }
        }
        interrupted = true
        assertThrows(InterruptedException::class.java) {
            f.s.tx { broken.find(own.spaceId, own.accountId, own.deviceId) }
        }
        assertEquals(before, head())
    }

    @Test
    fun archiveRestoreKeepsPendingRevisionsExactLeasesAndOriginalEncryptedCapability() {
        success(append(event(1)))
        val lease = claim().single().lease!!
        success(append(event(2)))
        val before = head()
        val key = success(f.s.tx { f.pushBindings.key(own) })!!.value
        val restored = f.s.restore()
        val sql = DSL.using(TransactionAwareDataSourceProxy(restored), SQLDialect.POSTGRES)
        val restoredQueue =
            StoredCollaborationSpacePushQueueRepository(
                PostgresCollaborationSpacePushQueueDataSource(sql)
            )
        val restoredBindings =
            EncryptedCollaborationSpacePushBindingRepository(
                PostgresCollaborationSpacePushBindingDataSource(sql),
                JceIdentityTokenDataSource(f.s.currentKeys),
            )
        assertEquals(
            before,
            success(
                f.s.transactions(restored).run(f.s.actor) {
                    restoredQueue.find(own.spaceId, own.accountId, own.deviceId)
                }
            ),
        )
        assertEquals(
            key,
            success(f.s.transactions(restored).run(f.s.actor) { restoredBindings.key(own) })!!.value,
        )
        val restoredWorker =
            DriverManagerDataSource(
                requireNotNull(restored.url),
                "space_gateway_worker",
                "isolated-worker-only",
            )
        val restoredWorkerQueue =
            StoredCollaborationSpacePushQueueRepository(
                PostgresCollaborationSpacePushQueueDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(restoredWorker), SQLDialect.POSTGRES)
                )
            )
        assertTrue(
            success(
                f.s.transactions(restoredWorker).run(f.s.actor) {
                    restoredWorkerQueue.finish(lease)
                }
            )
        )
        val after =
            success(
                f.s.transactions(restored).run(f.s.actor) {
                    restoredQueue.find(own.spaceId, own.accountId, own.deviceId)
                }
            )!!
        assertEquals(1L, after.deliveredRevision)
        assertEquals(2L, after.revision)
        assertEquals(event(2), after.event)
        assertNull(after.lease)
        assertEquals(before, head())
    }

    @Test
    fun rawIdentityDeletionAndRevisionResetRemainForbidden() {
        success(append(event(1)))
        val before = head()
        for (statement in
            listOf(
                "delete from collaboration_space_push_hints where device_id=?",
                "update collaboration_space_push_hints set revision=revision+2 where device_id=?",
                "update collaboration_space_push_hints set delivered_revision=revision where device_id=?",
                "update collaboration_space_push_hints set native_session_id=gen_random_uuid() where device_id=?",
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                f.s.tx { safeDatabaseCall { f.s.runtime.update(statement, own.deviceId) } },
            )
            assertEquals(before, head())
        }
    }

    @Test
    fun malformedAndForeignAcquiredRowsNeverBecomeOwnedQueueMetadata() {
        success(append(event(1)))
        val faults:
            List<(dev.fajar.hris.schema.tables.records.CollaborationSpacePushHintsRecord) -> Unit> =
            listOf(
                { it.accountId = UUID.randomUUID() },
                { it.spaceId = UUID.randomUUID() },
                { it.deviceId = UUID.randomUUID() },
                { it.leaseToken = UUID.randomUUID() },
                { it.deliveredRevision = it.revision + 1 },
                { it.expiresAt = it.lastHintAt.plusDays(2) },
                { it.recentEventHashes = arrayOf("a".repeat(64), "a".repeat(64)) },
                { it.recentEventHashes = arrayOf("malformed") },
            )
        val before = head()
        for (fault in faults) {
            val invalid =
                object : CollaborationSpacePushQueueDataSource by source {
                    override fun find(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                        lock: Boolean,
                    ) = source.find(spaceId, accountId, deviceId, lock)?.also(fault)
                }
            val broken = StoredCollaborationSpacePushQueueRepository(invalid)
            assertInstanceOf(
                Result.Failed::class.java,
                f.s.tx { broken.find(own.spaceId, own.accountId, own.deviceId) },
            )
            assertEquals(before, head())
        }
    }

    @Test
    fun nativeParentPurgeRetainsOwnedHintMetadataWithoutGrantingDeliveryAuthority() {
        success(append(event(1), peer))
        val before = head(peer)
        f.s.owner.update("delete from native_sessions where id=?", peer.nativeSessionId)
        assertEquals(before, head(peer))
        val retained =
            success(
                f.s.tx(f.s.actor.copy(accountId = peer.accountId)) {
                    f.pushBindings.find(peer.spaceId, peer.accountId, peer.deviceId)
                }
            )
        assertEquals(peer, retained)
        assertNull(success(f.s.tx { repo.find(peer.spaceId, peer.accountId, peer.deviceId) }))
        // Queue retention is not a delivery acceptance; future callback/worker admission
        // must retire this exact missing hard parent before sending an opaque wake.
    }

    private fun event(index: Int) =
        CollaborationPushEvent("!fictional-group:matrix.example.test", "$" + "fictional-$index")

    private fun append(
        event: CollaborationPushEvent?,
        binding: CollaborationSpacePushBinding = own,
        lifetime: Int = 300,
    ) =
        f.s.tx(f.s.actor.copy(accountId = binding.accountId)) {
            repo.append(binding, event, lifetime)
        }

    private fun head(binding: CollaborationSpacePushBinding = own) =
        requireNotNull(
            success(
                f.s.tx(f.s.actor.copy(accountId = binding.accountId)) {
                    repo.find(binding.spaceId, binding.accountId, binding.deviceId)
                }
            )
        )

    private fun claim(maximumAttempts: Int = 8) =
        success(worker { workerRepo.claim(UUID.randomUUID(), 8, 30, maximumAttempts) })

    private fun <T> worker(block: () -> Result<T>) = f.workerTransactions.run(f.s.actor, block)

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            result,
            (result as? Result.Failed)?.failure?.code,
        )
        return (result as Result.Success).value
    }

    private fun reserve(identity: Identity): CollaborationSpacePushBinding {
        val value =
            CollaborationSpacePushBinding(
                f.s.space,
                identity.account,
                identity.device,
                identity.parent,
                user(f.s, identity.account),
                "HRISG_${compact(identity.device)}",
                0,
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                null,
            )
        success(
            f.s.tx(f.s.actor.copy(accountId = identity.account)) { f.pushBindings.reserve(value) }
        )
        return value
    }

    private fun user(s: SpaceGatewayPostgresFixture, account: UUID) =
        "@hrisg_${compact(s.space)}_${compact(account)}:matrix.example.test"

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun seed(s: SpaceGatewayPostgresFixture, id: Identity) {
        val at = s.at.toString()
        val matrix = user(s, id.account)
        val device = "HRISG_${compact(id.device)}"
        s.owner.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional queue cohort')",
            id.account,
            "${id.account}@example.test",
        )
        s.owner.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            s.company,
            id.account,
        )
        s.owner.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
            s.company,
            id.account,
        )
        s.owner.update(
            "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
            id.parent,
            id.account,
            compact(id.parent).repeat(2),
            s.actor.authenticatedAt.toString(),
            s.at.plusSeconds(3600).toString(),
            s.at.plusSeconds(900).toString(),
            s.at.minusSeconds(60).toString(),
            s.at.minusSeconds(30).toString(),
        )
        s.owner.update(
            "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
            s.space,
            id.account,
            matrix,
            at,
        )
        s.owner.update(
            "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,'SPACE',?,'ANDROID','Fictional queue device','PROVISIONING',0,?::timestamptz,?::timestamptz)",
            s.space,
            id.device,
            id.account,
            matrix,
            device,
            id.parent,
            at,
            at,
        )
        s.owner.update(
            "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
            s.space,
            id.device,
            id.account,
            id.parent,
            at,
        )
        s.owner.update(
            "insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,?,0,'fictional-cohort-provider',?::timestamptz,?::timestamptz,?::timestamptz)",
            s.space,
            id.device,
            id.account,
            id.parent,
            matrix,
            device,
            at,
            at,
            s.at.plusSeconds(600).toString(),
        )
        s.owner.update(
            "update collaboration_space_devices set state='ACTIVE',version=1 where id=?",
            id.device,
        )
    }
}
