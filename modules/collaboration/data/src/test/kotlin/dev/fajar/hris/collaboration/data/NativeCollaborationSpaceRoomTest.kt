package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceRoomTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun stableIntentSurvivesFirstWriteAfterRotationAndLaterUpgradeWithoutGuessingProof() {
        val f = Fixture()
        val operation = UUID.randomUUID()
        val original =
            CollaborationSpaceRoomRequestIntent(
                operation,
                f.s.space,
                f.conversation,
                f.s.device,
                1,
                null,
            )
        f.s.owner.update("update native_sessions set version=1 where id=?", f.s.parent)
        val receipt =
            success(
                f.open(
                    operation,
                    parentVersion = 1,
                    format = CollaborationSpaceRoomRequestFormat.STABLE_INTENT,
                )
            )
        val first = f.counts()
        val build = f.f.client.copy(build = 6)
        f.s.owner.update("update native_sessions set version=2 where id=?", f.s.parent)
        assertEquals(
            receipt.copy(replayed = true),
            success(
                f.open(
                    operation,
                    parentVersion = 2,
                    client = build,
                    format = CollaborationSpaceRoomRequestFormat.STABLE_INTENT,
                )
            ),
        )
        val read = success(f.receipt(original, currentVersion = 2, client = build))
        assertEquals(original, read.original)
        assertEquals(receipt.copy(replayed = true), read.receipt)
        assertEquals(first, f.counts())
        assertNull(
            success(f.receipt(f.original(operation), currentVersion = 2, client = build)).receipt
        )
        val request =
            success(f.s.tx { f.rooms.provisioning(f.s.space, f.s.account, f.conversation) })!!
                .request
        assertEquals(1L, request.nativeSessionVersion)
        assertEquals(f.f.client, request.client)
        assertEquals(first, f.counts())
    }

    @Test
    fun stableReceiptRetainsUncertainAbsenceAndCurrentDenialOverridesChangedPayload() {
        val f = Fixture()
        val operation = UUID.randomUUID()
        val original =
            CollaborationSpaceRoomRequestIntent(
                operation,
                f.s.space,
                f.conversation,
                f.s.device,
                1,
                null,
            )
        val before = f.counts()
        assertNull(success(f.receipt(original)).receipt)
        assertEquals(before, f.counts())
        val receipt =
            success(f.open(operation, format = CollaborationSpaceRoomRequestFormat.STABLE_INTENT))
        val written = f.counts()
        assertEquals(receipt.copy(replayed = true), success(f.receipt(original)).receipt)
        val changed = original.copy(expectedDeviceVersion = 2)
        assertEquals(
            "operation_payload_mismatch",
            (f.receipt(changed) as Result.Failed).failure.code,
        )
        f.revokePeer()
        assertEquals(
            "collaboration_peer_unavailable",
            (f.receipt(changed) as Result.Failed).failure.code,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.open(operation, format = CollaborationSpaceRoomRequestFormat.STABLE_INTENT),
        )
        assertEquals(written, f.counts())
    }

    @Test
    fun legacyAndStableReceiptsWithTheSameUuidNeverAliasEachOther() {
        val f = Fixture()
        val operation = UUID.randomUUID()
        val original =
            CollaborationSpaceRoomRequestIntent(
                operation,
                f.s.space,
                f.conversation,
                f.s.device,
                1,
                null,
            )
        val legacy = success(f.open(operation))
        val first = f.counts()
        assertNull(success(f.receipt(original)).receipt)
        assertEquals(first, f.counts())
        assertEquals(
            legacy,
            success(f.open(operation, format = CollaborationSpaceRoomRequestFormat.STABLE_INTENT)),
        )
        val after = f.counts()
        assertEquals(first.getValue("operation_receipts") + 1, after.getValue("operation_receipts"))
        for (table in first.keys - "operation_receipts") assertEquals(first[table], after[table])
        assertEquals(legacy.copy(replayed = true), success(f.receipt(original)).receipt)
        assertEquals(
            legacy.copy(replayed = true),
            success(f.receipt(f.original(operation))).receipt,
        )
    }

    @Test
    fun nativeRequestAndReplayRetainOneIntentAndQueueWithoutProviderIo() {
        val f = Fixture()
        val operation = UUID.randomUUID()
        val receipt = success(f.open(operation))
        val firstCounts = f.counts()
        assertEquals(MutationReceipt(f.conversation, 0), receipt)
        assertEquals(receipt.copy(replayed = true), success(f.open(operation)))
        assertEquals(firstCounts, f.counts())
        val request =
            success(f.s.tx { f.rooms.provisioning(f.s.space, f.s.account, f.conversation) })!!
                .request
        assertEquals(f.s.parent, request.nativeSessionId)
        assertEquals(f.s.device, request.deviceId)
        assertEquals(1L, request.deviceVersion)
        assertEquals(0L, request.providerGeneration)
        assertEquals(f.f.client, request.client)
        val intent = success(f.s.tx { f.rooms.intent(f.s.space, f.s.account, f.conversation) })!!
        assertEquals(f.s.account, intent.requestedBy)
        assertEquals(f.s.account, intent.matrixCreatorAccountId)
        assertEquals(receipt, success(f.open()))
        val after = f.counts()
        assertEquals(
            firstCounts.getValue("operation_receipts") + 1,
            after.getValue("operation_receipts"),
        )
        for (key in firstCounts.keys - "operation_receipts") assertEquals(
            firstCounts[key],
            after[key],
        )
        val status = success(f.read())
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, status.provisioning!!.state)
        assertNull(status.room)
    }

    @Test
    fun recordedRoomRequestRetainsItsOriginalFingerprintAcrossNativeRotationAndBuildChanges() {
        val f = Fixture()
        val operation = UUID.randomUUID()
        success(f.open(operation))
        val originalStatus = success(f.read())
        val originalCounts = f.counts()

        val newBuild = f.open(operation, client = f.f.client.copy(build = f.f.client.build + 1))
        assertEquals("operation_payload_mismatch", (newBuild as Result.Failed).failure.code)
        assertEquals(originalCounts, f.counts())

        // Native refresh increments this version. Retained operation input must not be rewritten.
        assertEquals(
            1,
            f.s.owner.update(
                "update native_sessions set version=1 where id=? and version=0",
                f.s.parent,
            ),
        )
        assertEquals(originalStatus, success(f.read(parentVersion = 1)))
        val rotated = f.open(operation, parentVersion = 1)
        assertEquals("operation_payload_mismatch", (rotated as Result.Failed).failure.code)
        assertInstanceOf(Result.Failed::class.java, f.open(operation))
        assertEquals(originalCounts, f.counts())
        val retained =
            success(f.s.tx { f.rooms.provisioning(f.s.space, f.s.account, f.conversation) })!!
        assertEquals(0L, retained.request.nativeSessionVersion)
        assertEquals(f.f.client, retained.request.client)
        assertEquals(0L, retained.request.revision)
        assertEquals(originalStatus, success(f.read(parentVersion = 1)))
    }

    @Test
    fun journalFailureCancellationAndFinalExpiryRollBackAllNativeRequestWrites() {
        val f = Fixture()
        val before = f.counts()
        val start = f.f.clock.instant()
        for (mode in listOf("failed", "cancel", "interrupt", "expired", "peer")) {
            val journal =
                object : ChangeJournalRepository {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                        f.f.journal.record(actor, change).flatMap {
                            when (mode) {
                                "failed" ->
                                    Result.Failed(
                                        Failure(FailureKind.UNAVAILABLE, "fixture_journal_failure")
                                    )
                                "cancel" -> throw CancellationException("fixture room journal")
                                "interrupt" -> throw InterruptedException("fixture room journal")
                                "expired" -> {
                                    f.f.clock.at = start.plusSeconds(30)
                                    Result.Success(Unit)
                                }
                                else -> {
                                    f.revokePeer()
                                    Result.Success(Unit)
                                }
                            }
                        }
                }
            when (mode) {
                "cancel" ->
                    assertThrows(CancellationException::class.java) { f.open(journal = journal) }
                "interrupt" ->
                    assertThrows(InterruptedException::class.java) { f.open(journal = journal) }
                else -> assertInstanceOf(Result.Failed::class.java, f.open(journal = journal), mode)
            }
            assertEquals(before, f.counts(), mode)
            f.f.clock.at = start
            if (mode == "peer") f.grantPeer()
        }
    }

    @Test
    fun explicitReceiptReadUsesCurrentAdmissionAndKeepsOriginalProofAfterRotation() {
        val f = Fixture()
        val original = f.original(UUID.randomUUID())
        val recorded = success(f.open(original.operationId))
        val before = f.counts()
        f.s.owner.update("update native_sessions set version=1 where id=?", f.s.parent)
        val recovered =
            success(
                f.receipt(
                    original,
                    currentVersion = 1,
                    client = f.f.client.copy(build = 6),
                    policy = CollaborationSpacePolicy(true, 6, 6),
                )
            )
        assertEquals(original, recovered.original)
        assertEquals(recorded.copy(replayed = true), recovered.receipt)
        assertInstanceOf(Result.Failed::class.java, f.receipt(original))
        assertInstanceOf(
            Result.Failed::class.java,
            f.receipt(original, currentVersion = 1, policy = CollaborationSpacePolicy(true, 6, 6)),
        )
        assertEquals(before, f.counts())
        val retained =
            success(f.s.tx { f.rooms.provisioning(f.s.space, f.s.account, f.conversation) })!!
        assertEquals(0L, retained.request.nativeSessionVersion)
        assertEquals(original.client, retained.request.client)
    }

    @Test
    fun originalReceiptSurvivesRevocationAndPurgeWithoutRevivingItsParent() {
        val f = Fixture()
        val original = f.original(UUID.randomUUID())
        val recorded = success(f.open(original.operationId))
        val replacement = UUID.randomUUID()
        f.s.owner.update(
            "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) select ?,account_id,?,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at from native_sessions where id=?",
            replacement,
            replacement.toString().replace("-", "").repeat(2),
            f.s.parent,
        )
        f.s.owner.update("update native_sessions set revoked_at=now() where id=?", f.s.parent)
        assertEquals(
            recorded.copy(replayed = true),
            success(f.receipt(original, currentParent = replacement)).receipt,
        )
        assertInstanceOf(Result.Failed::class.java, f.receipt(original))
        f.s.owner.update("delete from native_sessions where id=?", f.s.parent)
        val afterPurge = f.counts()
        assertEquals(
            recorded.copy(replayed = true),
            success(f.receipt(original, currentParent = replacement)).receipt,
        )
        assertEquals(afterPurge, f.counts())
        assertEquals(
            0,
            f.s.owner.queryForObject(
                "select count(*) from native_sessions where id=?",
                Int::class.java,
                f.s.parent,
            ),
        )
    }

    @Test
    fun completeOriginalFingerprintIsRequiredAndAnAbsentReceiptCannotFenceLateWork() {
        val f = Fixture()
        val original = f.original(UUID.randomUUID())
        val empty = f.counts()
        val missing = success(f.receipt(original))
        assertEquals(original, missing.original)
        assertNull(missing.receipt)
        assertEquals(empty, f.counts())
        val recorded = success(f.open(original.operationId))
        assertEquals(recorded.copy(replayed = true), success(f.receipt(original)).receipt)
        val before = f.counts()
        for (changed in
            listOf(
                original.copy(nativeSessionId = UUID.randomUUID()),
                original.copy(nativeSessionVersion = 1),
                original.copy(deviceId = UUID.randomUUID()),
                original.copy(expectedDeviceVersion = 2),
                original.copy(client = original.client.copy(build = 6)),
                original.copy(expectedAttempt = CollaborationSpaceRoomObservedAttempt(f.peer, 0)),
            )) assertEquals(
            "operation_payload_mismatch",
            (f.receipt(changed) as Result.Failed).failure.code,
        )
        assertEquals(before, f.counts())
    }

    @Test
    fun aPeerCannotReadAnotherActorsReceiptAndInvalidDescriptorsCannotReachItsStore() {
        val f = Fixture()
        val original = f.original(UUID.randomUUID())
        success(f.open(original.operationId))
        val before = f.counts()
        assertNull(
            success(f.receipt(original, actor = f.peerActor, currentParent = f.peerParent)).receipt
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.receipt(original, actor = f.s.actor.copy(companyId = f.s.company)),
        )
        val noRead =
            object : OperationRepository by f.f.operations {
                override fun lockAndReplay(
                    actor: Actor,
                    key: OperationKey,
                ): Result<MutationReceipt?> = error("Invalid descriptor reached receipt storage")
            }
        val nil = UUID(0, 0)
        for (invalid in
            listOf(
                original.copy(operationId = nil),
                original.copy(spaceId = nil),
                original.copy(conversationId = nil),
                original.copy(nativeSessionId = nil),
                original.copy(deviceId = nil),
                original.copy(nativeSessionVersion = -1),
                original.copy(nativeSessionVersion = 1_000_000),
                original.copy(expectedDeviceVersion = -1),
                original.copy(expectedDeviceVersion = 1_000_000),
                original.copy(expectedAttempt = CollaborationSpaceRoomObservedAttempt(nil, 0)),
                original.copy(
                    expectedAttempt = CollaborationSpaceRoomObservedAttempt(f.peer, 1_000_000)
                ),
                original.copy(client = original.client.copy(platform = ClientPlatform.WEB)),
                original.copy(client = original.client.copy(build = 0)),
            )) assertEquals(
            "invalid_collaboration_room_operation",
            (f.receipt(invalid, operations = noRead) as Result.Failed).failure.code,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.receipt(original, operations = noRead, policy = CollaborationSpacePolicy(false, 5, 5)),
        )
        assertEquals(before, f.counts())
    }

    @Test
    fun aStoredReceiptForAnotherConversationOrAnInvalidRevisionCannotBePublished() {
        val f = Fixture()
        val original = f.original(UUID.randomUUID())
        val before = f.counts()
        for (corrupted in
            listOf(
                MutationReceipt(UUID.randomUUID(), 0),
                MutationReceipt(f.conversation, -1),
                MutationReceipt(f.conversation, 1_000_000),
            )) {
            val source =
                object : OperationRepository by f.f.operations {
                    override fun lockAndReplay(
                        actor: Actor,
                        key: OperationKey,
                    ): Result<MutationReceipt?> = Result.Success(corrupted)
                }
            assertEquals(
                "collaboration_room_provisioning_changed",
                (f.receipt(original, operations = source) as Result.Failed).failure.code,
            )
        }
        assertEquals(before, f.counts())
    }

    @Test
    fun currentDenialAndCapturedDeadlineOverrideSuccessfulAndFailedReceiptAcquisition() {
        val f = Fixture()
        val original = f.original(UUID.randomUUID())
        success(f.open(original.operationId))
        val before = f.counts()
        f.revokePeer()
        for (query in listOf(original, original.copy(nativeSessionVersion = 1))) {
            assertEquals(
                "collaboration_peer_unavailable",
                (f.receipt(query) as Result.Failed).failure.code,
            )
        }
        f.grantPeer()
        assertInstanceOf(
            Result.Failed::class.java,
            f.receipt(original, currentParent = f.peerParent),
        )
        val start = f.f.clock.instant()
        for (query in listOf(original, original.copy(nativeSessionVersion = 1))) {
            val slow =
                object : OperationRepository by f.f.operations {
                    override fun lockAndReplay(
                        actor: Actor,
                        key: OperationKey,
                    ): Result<MutationReceipt?> {
                        val acquired = f.f.operations.lockAndReplay(actor, key)
                        f.f.clock.at = start.plusSeconds(30)
                        return acquired
                    }
                }
            assertEquals(
                "collaboration_room_provisioning_changed",
                (f.receipt(query, operations = slow) as Result.Failed).failure.code,
            )
            f.f.clock.at = start
        }
        var reads = 0
        val late =
            object : CollaborationSpaceConversationRepository by f.conversations {
                override fun member(
                    spaceId: UUID,
                    accountId: UUID,
                ): Result<CollaborationSpaceMember?> {
                    val acquired = f.conversations.member(spaceId, accountId)
                    if (++reads == 3) f.f.clock.at = start.plusSeconds(30)
                    return acquired
                }
            }
        assertEquals(
            "collaboration_room_provisioning_changed",
            (f.receipt(original, conversations = late) as Result.Failed).failure.code,
        )
        f.f.clock.at = start
        assertEquals(before, f.counts())
    }

    @Test
    fun receiptReadPreservesCancellationAndWaitsForAnOriginalUncommittedWriter() =
        assertReceiptReadWaitsForWriter(CollaborationSpaceRoomRequestFormat.NATIVE_PROOF)

    @Test
    fun stableReceiptReadPreservesCancellationAndWaitsForItsOriginalUncommittedWriter() =
        assertReceiptReadWaitsForWriter(CollaborationSpaceRoomRequestFormat.STABLE_INTENT)

    private fun assertReceiptReadWaitsForWriter(format: CollaborationSpaceRoomRequestFormat) {
        val f = Fixture()
        val operation = UUID.randomUUID()
        val original: CollaborationSpaceRoomReceiptDescriptor =
            when (format) {
                CollaborationSpaceRoomRequestFormat.NATIVE_PROOF -> f.original(operation)
                CollaborationSpaceRoomRequestFormat.STABLE_INTENT ->
                    CollaborationSpaceRoomRequestIntent(
                        operation,
                        f.s.space,
                        f.conversation,
                        f.s.device,
                        1,
                        null,
                    )
            }
        val before = f.counts()
        for (interrupted in listOf(false, true)) {
            val cancelled =
                object : OperationRepository by f.f.operations {
                    override fun lockAndReplay(
                        actor: Actor,
                        key: OperationKey,
                    ): Result<MutationReceipt?> {
                        f.f.operations.lockAndReplay(actor, key)
                        if (interrupted) throw InterruptedException("fictional receipt read")
                        throw CancellationException("fictional receipt read")
                    }
                }
            if (interrupted)
                assertThrows(InterruptedException::class.java) {
                    f.receipt(original, operations = cancelled)
                }
            else
                assertThrows(CancellationException::class.java) {
                    f.receipt(original, operations = cancelled)
                }
            assertEquals(before, f.counts())
        }
        val held = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val reading = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val journal =
                object : ChangeJournalRepository {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                        f.f.journal.record(actor, change).map {
                            held.countDown()
                            assertTrue(finish.await(10, TimeUnit.SECONDS))
                        }
                }
            val writer =
                pool.submit<Result<MutationReceipt>> {
                    f.open(original.operationId, journal = journal, format = format)
                }
            assertTrue(held.await(10, TimeUnit.SECONDS))
            val operations =
                object : OperationRepository by f.f.operations {
                    override fun lockAndReplay(
                        actor: Actor,
                        key: OperationKey,
                    ): Result<MutationReceipt?> {
                        reading.countDown()
                        return f.f.operations.lockAndReplay(actor, key)
                    }
                }
            val reader =
                pool.submit<
                    Result<
                        CollaborationSpaceRoomReceiptObservation<
                            CollaborationSpaceRoomReceiptDescriptor
                        >
                    >
                > {
                    f.receipt(original, operations = operations)
                }
            assertTrue(reading.await(10, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { reader.get(200, TimeUnit.MILLISECONDS) }
            finish.countDown()
            assertEquals(
                success(writer.get(10, TimeUnit.SECONDS)).copy(replayed = true),
                success(reader.get(10, TimeUnit.SECONDS)).receipt,
            )
        } finally {
            finish.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun terminalRetryRequiresObservedRevisionAndCooldownWithoutChangingFirstCreator() {
        val f = Fixture()
        success(f.open())
        assertInstanceOf(Result.Failed::class.java, f.open(revision = 0))
        success(f.s.tx { f.rooms.cancel(f.s.space, f.s.account, f.conversation, 0) })
        val early = f.open(revision = 0)
        assertEquals(
            "collaboration_provisioning_retry_too_soon",
            (early as Result.Failed).failure.code,
        )
        val previous =
            success(f.s.tx { f.rooms.provisioning(f.s.space, f.s.account, f.conversation) })!!
        val earliest = previous.request.requestedAt.plusSeconds(60)
        Thread.sleep(
            java.time.Duration.between(Instant.now(), earliest).toMillis().coerceAtLeast(0) + 30
        )
        f.f.clock.at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val operation = UUID.randomUUID()
        assertEquals(MutationReceipt(f.conversation, 1), success(f.open(operation, revision = 0)))
        val next =
            success(f.s.tx { f.rooms.provisioning(f.s.space, f.s.account, f.conversation) })!!
        assertEquals(previous.lastOutcome, next.lastOutcome)
        assertEquals(previous.createdAt, next.createdAt)
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, next.state)
        assertEquals(1L, next.request.revision)
        assertEquals(
            MutationReceipt(f.conversation, 1, replayed = true),
            success(f.open(operation, revision = 0)),
        )
        assertInstanceOf(Result.Failed::class.java, f.open(revision = 0))
        assertEquals(
            f.s.account,
            success(f.s.tx { f.rooms.intent(f.s.space, f.s.account, f.conversation) })!!.requestedBy,
        )
    }

    @Test
    fun peerStatusUsesItsOwnNativeProofAndFinalAdmissionDominatesEitherReadOutcome() {
        val f = Fixture()
        success(f.open())
        assertEquals(success(f.read()), success(f.read(actor = f.peerActor, parent = f.peerParent)))
        assertNull(
            success(
                f.s.tx(f.peerActor) { f.rooms.provisioning(f.s.space, f.s.account, f.conversation) }
            )
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.read(actor = f.peerActor.copy(companyId = f.other), parent = f.peerParent),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.read(actor = f.peerActor, parent = f.s.parent),
        )
        val start = f.f.clock.instant()
        for (failed in listOf(false, true)) {
            val rooms =
                object : CollaborationSpaceRoomRepository by f.rooms {
                    override fun status(
                        spaceId: UUID,
                        accountId: UUID,
                        conversationId: UUID,
                    ): Result<CollaborationSpaceRoomStatus?> {
                        val acquired = f.rooms.status(spaceId, accountId, conversationId)
                        f.f.clock.at = start.plusSeconds(30)
                        return if (failed)
                            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_read_failure"))
                        else acquired
                    }
                }
            val result = f.read(rooms = rooms)
            assertEquals(
                "collaboration_room_provisioning_changed",
                (result as Result.Failed).failure.code,
            )
            f.f.clock.at = start
        }
        f.revokePeer()
        assertInstanceOf(Result.Failed::class.java, f.read())
        assertInstanceOf(Result.Failed::class.java, f.open())
    }

    @Test
    fun groupOriginalGuardSerializesWithTheExistingCompanyProducer() {
        val f = Fixture()
        val old = PostgresCollaborationDirectDataSource(f.f.sql)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val producer =
                pool.submit<Result<Unit>> {
                    f.s.tx(f.s.actor.copy(companyId = f.s.company)) {
                        old.lockPair(f.s.company, f.pair[0], f.pair[1])
                        held.countDown()
                        assertTrue(release.await(10, TimeUnit.SECONDS))
                        Result.Success(Unit)
                    }
                }
            assertTrue(held.await(10, TimeUnit.SECONDS))
            val group =
                pool.submit<Result<Unit>> {
                    f.s.tx { f.rooms.lockOriginalPair(f.s.company, f.pair[0], f.pair[1]) }
                }
            var blocked = false
            repeat(100) {
                if (!blocked) {
                    blocked =
                        f.s.owner.queryForObject(
                            "select exists(select 1 from pg_locks where locktype='advisory' and not granted)",
                            Boolean::class.java,
                        ) == true
                    if (!blocked) TimeUnit.MILLISECONDS.sleep(10)
                }
            }
            assertTrue(blocked)
            release.countDown()
            success(producer.get(10, TimeUnit.SECONDS))
            success(group.get(10, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private inner class Fixture {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val s = f.storage
        val peer = UUID.randomUUID()
        val other = UUID.randomUUID()
        val peerParent = UUID.randomUUID()
        val peerActor = s.actor.copy(accountId = peer)
        val conversation = UUID.randomUUID()
        val pair = listOf(s.account, peer).sortedBy(UUID::toString)

        init {
            Flyway.configure().dataSource(s.source).target("138").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
            s.owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional native room peer company','UTC')",
                other,
                other.toString().replace("-", ""),
            )
            s.owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional native room peer')",
                peer,
                "$peer@example.test",
            )
            s.owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                other,
                peer,
            )
            grantPeer()
            s.owner.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
                s.space,
                peer,
                "@hrisg_${s.space.toString().replace("-", "")}_${peer.toString().replace("-", "")}:matrix.example.test",
                s.at.toString(),
            )
            s.owner.update(
                "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                s.space,
                conversation,
                pair[0],
                pair[1],
                peer,
                s.at.toString(),
            )
            s.owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                peerParent,
                peer,
                peerParent.toString().replace("-", "").repeat(2),
                peerActor.authenticatedAt.toString(),
                s.at.plusSeconds(3600).toString(),
                s.at.plusSeconds(900).toString(),
                s.at.minusSeconds(60).toString(),
                s.at.minusSeconds(30).toString(),
            )
            // Shared bootstrap fixtures use an old clock. Queue requests have a real
            // sixty-second admission deadline, so keep it live against PostgreSQL time.
            f.clock.at = Instant.now().minusSeconds(45).truncatedTo(ChronoUnit.MICROS)
        }

        val rooms =
            StoredCollaborationSpaceRoomRepository(PostgresCollaborationSpaceRoomDataSource(f.sql))
        val conversations =
            StoredCollaborationSpaceConversationRepository(
                PostgresCollaborationSpaceConversationDataSource(f.sql)
            )

        fun command(journal: ChangeJournalRepository = f.journal) =
            RequestNativeCollaborationSpaceRoom(
                rooms,
                conversations,
                f.registry,
                f.providers,
                f.selections,
                f.legacy,
                f.completions,
                f.provisioning,
                f.spaces,
                f.sessions,
                f.identities,
                f.operations,
                journal,
                s.transactions,
                IdentitySecurityPolicy(),
                CollaborationSpacePolicy(true, 5, 5),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                f.clock,
            )

        fun open(
            operation: UUID = UUID.randomUUID(),
            revision: Long? = null,
            journal: ChangeJournalRepository = f.journal,
            parentVersion: Long = 0,
            client: ClientVersion = f.client,
            format: CollaborationSpaceRoomRequestFormat =
                CollaborationSpaceRoomRequestFormat.NATIVE_PROOF,
        ) =
            command(journal)
                .execute(
                    s.actor,
                    s.parent,
                    parentVersion,
                    s.space,
                    operation,
                    conversation,
                    s.device,
                    1,
                    revision,
                    client,
                    revision?.let { s.account },
                    format,
                )

        fun read(
            actor: Actor = s.actor,
            parent: UUID = s.parent,
            rooms: CollaborationSpaceRoomRepository = this.rooms,
            parentVersion: Long = 0,
        ) =
            ReadNativeCollaborationSpaceRoom(
                    rooms,
                    conversations,
                    f.spaces,
                    f.sessions,
                    f.identities,
                    s.transactions,
                    CollaborationSpacePolicy(true, 5, 5),
                    CollaborationMatrixPolicy(true, "matrix.example.test"),
                    IdentitySecurityPolicy(),
                    f.clock,
                )
                .execute(actor, parent, parentVersion, s.space, conversation, f.client)

        fun original(operation: UUID) =
            CollaborationSpaceRoomOperation(
                operation,
                s.space,
                conversation,
                s.parent,
                0,
                s.device,
                1,
                null,
                f.client,
            )

        fun <T : CollaborationSpaceRoomReceiptDescriptor> receipt(
            original: T,
            currentParent: UUID = s.parent,
            currentVersion: Long = 0,
            client: ClientVersion = f.client,
            operations: OperationRepository = f.operations,
            conversations: CollaborationSpaceConversationRepository = this.conversations,
            actor: Actor = s.actor,
            policy: CollaborationSpacePolicy = CollaborationSpacePolicy(true, 5, 5),
        ) =
            ReadNativeCollaborationSpaceRoomReceipt(
                    operations,
                    rooms,
                    conversations,
                    f.spaces,
                    f.sessions,
                    f.identities,
                    s.transactions,
                    policy,
                    IdentitySecurityPolicy(),
                    f.clock,
                )
                .execute(actor, currentParent, currentVersion, client, original)

        fun revokePeer() {
            s.owner.update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                peer,
            )
        }

        fun grantPeer() {
            s.owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                other,
                peer,
            )
        }

        fun counts() =
            listOf(
                    "collaboration_space_room_intents",
                    "collaboration_space_room_provisioning",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                )
                .associateWith {
                    s.owner.queryForObject("select count(*) from $it", Int::class.java)!!
                }
    }

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }
}
