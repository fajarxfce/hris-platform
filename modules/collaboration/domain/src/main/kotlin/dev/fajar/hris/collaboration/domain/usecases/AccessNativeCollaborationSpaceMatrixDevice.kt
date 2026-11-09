package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.NativePushRegistration
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Fixed Matrix device operations under current group ownership. */
class AccessNativeCollaborationSpaceMatrixDevice(
    private val gateway: CollaborationSpaceGatewaySessionRepository,
    private val requests: CollaborationSpaceRequestRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val providers: CollaborationSpaceProviderSessionRepository,
    private val matrix: CollaborationMatrixRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
    private val keys: CollaborationSpaceKeyRepository,
    private val providerKeys: CollaborationMatrixKeyRepository,
    private val journal: ChangeJournalRepository,
    private val recipients: CollaborationSpaceRecipientRepository,
    private val providerToDevice: CollaborationMatrixToDeviceRepository,
    private val rooms: CollaborationSpaceRoomRepository,
    private val providerRoomMessage: CollaborationMatrixRoomMessageRepository,
    private val providerRoomHistory: CollaborationSpaceMatrixRoomHistoryRepository,
    private val conversations: CollaborationSpaceConversationRepository,
    private val syncCursors: CollaborationSpaceSyncCursorRepository,
    private val providerSync: CollaborationSpaceMatrixSyncRepository,
    private val providerRoomAccess: CollaborationSpaceMatrixRoomAccessRepository,
    private val service: CollaborationServicePolicy,
    private val pushBindings: CollaborationSpacePushBindingRepository,
    private val providerPush: CollaborationMatrixPusherRepository,
    private val nativePush: NativePushRegistrationRepository,
    private val secretStorage: CollaborationSecretStorageRepository,
) {
    fun secretStorageDefaultKey(
        token: String,
        correlationId: UUID,
        userId: String,
    ): Result<CollaborationSecretStorageDefaultKey> =
        admitted(
            token,
            correlationId,
            decrypt = true,
            prepare = { observed ->
                if (userId == observed.device.matrixUserId) Result.Success(Unit)
                else
                    Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "collaboration_account_data_unavailable")
                    )
            },
        ) { exchange, _ ->
            exchange
                .exchange { bearer, deadline -> secretStorage.defaultKey(userId, bearer, deadline) }
                .flatMap {
                    it?.let { key -> Result.Success(key) }
                        ?: Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "collaboration_account_data_not_found")
                        )
                }
        }

    fun ensurePusher(token: String, correlationId: UUID): Result<Unit> {
        if (!service.enabled || !service.pushEnabled) return pushUnavailable()
        return admitted(
            token,
            correlationId,
            decrypt = true,
            prepare = ::preparePusher,
            revalidate = ::checkPusher,
        ) { exchange, prepared ->
            exchange
                .exchange { bearer, deadline ->
                    providerPush.set(
                        prepared.key.value,
                        bearer,
                        minOf(deadline, prepared.registration.expiresAt),
                    )
                }
                .flatMap { exchange.publishPusher(prepared.binding) }
        }
    }

    private class PusherPreparation(
        val binding: CollaborationSpacePushBinding,
        val key: CollaborationSpacePushKey,
        val registration: NativePushRegistration,
    )

    private fun preparePusher(observed: Observation): Result<PusherPreparation> {
        val device = observed.device
        return nativePush
            .find(device.accountId, device.originNativeSessionId)
            .flatMap registrationRead@{ registration ->
                if (!activePushRegistration(observed, registration))
                    return@registrationRead pushUnavailable()
                pushBindings
                    .lock(device.spaceId, device.id)
                    .flatMap { pushBindings.find(device.spaceId, device.accountId, device.id) }
                    .flatMap bindingRead@{ previous ->
                        val binding =
                            previous
                                ?: CollaborationSpacePushBinding(
                                    device.spaceId,
                                    device.accountId,
                                    device.id,
                                    device.originNativeSessionId,
                                    device.matrixUserId,
                                    device.matrixDeviceId,
                                    0,
                                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                                    null,
                                )
                        if (!validPushBinding(binding, observed)) return@bindingRead pushChanged()
                        val reserved =
                            if (previous != null) Result.Success(Unit)
                            else
                                pushBindings.reserve(binding).flatMap {
                                    journal.record(
                                        observed.actor,
                                        ChangeRecord(
                                            "collaboration_space_device",
                                            device.id,
                                            "collaboration.space_push_reserved",
                                            mapOf(
                                                "spaceId" to device.spaceId.toString(),
                                                "version" to "0",
                                            ),
                                        ),
                                    )
                                }
                        reserved
                            .flatMap { pushBindings.key(binding) }
                            .flatMap { key ->
                                if (key == null || !validCollaborationSpacePushKey(key.value))
                                    pushChanged()
                                else
                                    Result.Success(
                                        PusherPreparation(
                                            binding,
                                            key,
                                            requireNotNull(registration),
                                        )
                                    )
                            }
                    }
            }
    }

    private fun activePushRegistration(
        observed: Observation,
        value: NativePushRegistration?,
    ): Boolean {
        val now = clock.instant()
        return value != null &&
            value.accountId == observed.device.accountId &&
            value.sessionId == observed.device.originNativeSessionId &&
            value.platform.name == observed.device.platform.name &&
            value.enabled &&
            value.version in 0L..9999L &&
            value.registeredAt <= value.updatedAt &&
            value.updatedAt <= now &&
            value.expiresAt > now
    }

    private fun validPushBinding(
        value: CollaborationSpacePushBinding,
        observed: Observation,
    ): Boolean {
        val device = observed.device
        val at = clock.instant()
        return validCollaborationSpacePushBinding(value) &&
            value.spaceId == device.spaceId &&
            value.accountId == device.accountId &&
            value.deviceId == device.id &&
            value.nativeSessionId == device.originNativeSessionId &&
            value.matrixUserId == device.matrixUserId &&
            value.matrixDeviceId == device.matrixDeviceId &&
            value.reservedAt >= device.createdAt &&
            value.reservedAt <= at &&
            value.publishedAt?.let { it > at } != true
    }

    private fun checkPusher(observed: Observation, prepared: PusherPreparation): Result<Unit> =
        nativePush.find(observed.device.accountId, observed.device.originNativeSessionId).flatMap {
            current ->
            if (!activePushRegistration(observed, current)) pushUnavailable()
            else if (current != prepared.registration) pushChanged()
            else
                pushBindings
                    .find(
                        prepared.binding.spaceId,
                        prepared.binding.accountId,
                        prepared.binding.deviceId,
                    )
                    .flatMap { binding ->
                        if (
                            binding == null ||
                                !validPushBinding(binding, observed) ||
                                !samePushReservation(binding, prepared.binding)
                        )
                            pushChanged()
                        else Result.Success(Unit)
                    }
        }

    private fun samePushReservation(
        current: CollaborationSpacePushBinding,
        retained: CollaborationSpacePushBinding,
    ): Boolean =
        if (retained.version == 0L) current.copy(version = 0, publishedAt = null) == retained
        else current == retained

    private fun pushUnavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_push_unavailable"))

    private fun pushChanged() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_push_changed"))

    fun identity(token: String, correlationId: UUID): Result<CollaborationMatrixIdentity> =
        admitted(token, correlationId, decrypt = true) { observed, until ->
            matrix.whoAmI(requireNotNull(observed.credential).accessToken, until).flatMap {
                if (
                    it.userId != observed.device.matrixUserId ||
                        it.deviceId != observed.device.matrixDeviceId ||
                        it.isGuest
                )
                    Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response")
                    )
                else Result.Success(it)
            }
        }

    fun capabilities(token: String, correlationId: UUID): Result<Unit> =
        admitted(token, correlationId, decrypt = false) { _, _ -> Result.Success(Unit) }

    fun directMetadata(
        token: String,
        correlationId: UUID,
        userId: String,
    ): Result<Map<String, List<String>>> = directMetadata(token, correlationId, userId, null)

    fun confirmDirectMetadata(
        token: String,
        correlationId: UUID,
        userId: String,
        content: Map<String, List<String>>,
    ): Result<Unit> =
        normalizeCollaborationDirectMetadata(content).flatMap {
            directMetadata(token, correlationId, userId, it).map { Unit }
        }

    private fun directMetadata(
        token: String,
        correlationId: UUID,
        userId: String,
        confirmation: Map<String, List<String>>?,
    ): Result<Map<String, List<String>>> =
        admitted(
            token,
            correlationId,
            decrypt = false,
            prepare = { observed ->
                if (userId != observed.device.matrixUserId)
                    Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "collaboration_direct_metadata_unavailable")
                    )
                else
                    prepareReadScope(observed).flatMap { prepared ->
                        val current =
                            collaborationSpaceDirectMetadata(prepared.owner, prepared.rooms)
                        if (confirmation != null && confirmation != current)
                            Result.Failed(
                                Failure(
                                    FailureKind.CONFLICT,
                                    "collaboration_direct_metadata_managed",
                                )
                            )
                        else Result.Success(prepared)
                    }
            },
            revalidate = { observed, original ->
                prepareReadScope(observed).flatMap { current ->
                    if (current == original) Result.Success(Unit)
                    else
                        Result.Failed(
                            Failure(FailureKind.CONFLICT, "collaboration_direct_metadata_changed")
                        )
                }
            },
        ) { _, prepared ->
            Result.Success(collaborationSpaceDirectMetadata(prepared.owner, prepared.rooms))
        }

    fun joinRoom(token: String, correlationId: UUID, roomId: String): Result<String> =
        roomRead(token, correlationId, roomId) { exchange, prepared ->
            exchange.exchange { bearer, deadline ->
                providerRoomAccess.join(prepared.owner, prepared.room, bearer, deadline)
            }
        }

    fun roomState(
        token: String,
        correlationId: UUID,
        roomId: String,
    ): Result<CollaborationRoomEvents> =
        roomRead(token, correlationId, roomId) { exchange, prepared ->
            exchange.exchange { bearer, deadline ->
                providerRoomAccess.state(prepared.owner, prepared.room, bearer, deadline)
            }
        }

    fun roomMembers(
        token: String,
        correlationId: UUID,
        roomId: String,
        at: String?,
    ): Result<CollaborationRoomEvents> {
        if (
            at?.let {
                it.length !in 1..4096 || it.any { character -> character.code !in 33..126 }
            } == true
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_room_request")
            )
        return roomRead(token, correlationId, roomId) { exchange, prepared ->
            val opened = at?.let { syncCursors.open(prepared.owner, it) } ?: Result.Success(null)
            opened.flatMap { position ->
                // A bridged company cursor needs fresh group sync, not a different membership time.
                if (at != null && position == null)
                    Result.Failed(
                        Failure(FailureKind.CONFLICT, "collaboration_sync_reset_required")
                    )
                else
                    exchange.exchange { bearer, deadline ->
                        providerRoomAccess.members(
                            prepared.owner,
                            prepared.room,
                            position?.providerSince,
                            bearer,
                            deadline,
                        )
                    }
            }
        }
    }

    fun sendReadReceipt(
        token: String,
        correlationId: UUID,
        roomId: String,
        receiptType: String,
        eventId: String,
    ): Result<Unit> {
        if (
            receiptType !in setOf("m.read", "m.read.private") ||
                !validCollaborationRoomHistoryEventId(eventId)
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_read_receipt")
            )
        return roomRead(token, correlationId, roomId) { exchange, prepared ->
            readEvent(exchange, prepared, eventId).flatMap {
                exchange.exchange { bearer, deadline ->
                    providerRoomAccess.receipt(
                        prepared.owner,
                        prepared.room,
                        receiptType,
                        eventId,
                        bearer,
                        deadline,
                    )
                }
            }
        }
    }

    fun sync(
        token: String,
        correlationId: UUID,
        request: CollaborationSyncRequest,
    ): Result<CollaborationSyncResult> {
        if (
            request.timeoutMillis !in 0..30_000 ||
                request.since?.let {
                    it.length !in 1..4096 || it.any { character -> character.code !in 33..126 }
                } == true
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_sync_request")
            )
        return admitted(
            token,
            correlationId,
            decrypt = true,
            prepare = { prepareReadScope(it) },
            revalidate = { observed, prepared ->
                prepareReadScope(observed).flatMap { current ->
                    if (current == prepared) Result.Success(Unit)
                    else
                        Result.Failed(
                            Failure(FailureKind.UNAVAILABLE, "collaboration_sync_scope_changed")
                        )
                }
            },
        ) { exchange, prepared ->
            // Crypto and provider work run outside SQL. The outer runner confirms both outcomes,
            // including after cursor sealing, under the original request deadline.
            val opened =
                request.since?.let { syncCursors.open(prepared.owner, it) } ?: Result.Success(null)
            opened.flatMap { position ->
                exchange
                    .exchange { bearer, deadline ->
                        providerSync.sync(
                            prepared.owner,
                            prepared.rooms,
                            position?.takeIf { it.scopeDigest == prepared.rooms.digest },
                            request.timeoutMillis,
                            request.fullState,
                            bearer,
                            deadline,
                        )
                    }
                    .flatMap { batch ->
                        syncCursors
                            .seal(
                                prepared.owner,
                                CollaborationSyncPosition(
                                    prepared.rooms.digest,
                                    batch.providerSince,
                                    batch.providerFilterId,
                                ),
                            )
                            .map { CollaborationSyncResult(it, batch.payload) }
                    }
            }
        }
    }

    private fun prepareReadScope(observed: Observation): Result<ReadScopePreparation> {
        val device = observed.device
        val owner =
            CollaborationSpaceHistoryOwner(
                device.spaceId,
                device.accountId,
                device.id,
                provider.serverName,
                device.matrixUserId,
                device.matrixDeviceId,
                device.legacy,
            )
        if (!validCollaborationSpaceHistoryIdentity(owner)) return invalidProvider()
        return rooms
            .ownedBoundRooms(
                owner.spaceId,
                owner.accountId,
                owner.serverName,
                COLLABORATION_SYNC_ROOM_LIMIT + 1,
            )
            .flatMap { published ->
                val company = collaborationSpaceHistoryOriginalCompany(owner)
                val originals =
                    if (company == null) Result.Success(emptyList())
                    else
                        conversations.ownedLegacyRooms(
                            owner.spaceId,
                            owner.accountId,
                            company,
                            owner.serverName,
                            COLLABORATION_SYNC_ROOM_LIMIT + 1,
                        )
                originals.flatMap { original ->
                    snapshotCollaborationSpaceReadScope(owner, published, original, clock.instant())
                        .map { ReadScopePreparation(owner, it) }
                }
            }
    }

    private data class ReadScopePreparation(
        val owner: CollaborationSpaceHistoryOwner,
        val rooms: CollaborationSpaceReadScope,
    )

    fun roomMessages(
        token: String,
        correlationId: UUID,
        roomId: String,
        request: CollaborationRoomPageRequest,
    ): Result<CollaborationRoomHistory> {
        if (!validCollaborationRoomHistoryPage(request)) return invalidHistory()
        return roomRead(token, correlationId, roomId) { exchange, prepared ->
            exchange.exchange { bearer, deadline ->
                when (val room = prepared.room) {
                    is CollaborationSpaceReadRoom.Published ->
                        providerRoomHistory.messages(
                            prepared.owner,
                            room.value,
                            request,
                            bearer,
                            deadline,
                        )
                    is CollaborationSpaceReadRoom.Original ->
                        providerRoomHistory.messages(
                            prepared.owner,
                            room.value,
                            request,
                            bearer,
                            deadline,
                        )
                }
            }
        }
    }

    fun roomContext(
        token: String,
        correlationId: UUID,
        roomId: String,
        eventId: String,
        limit: Int,
        filter: CollaborationRoomHistoryFilter = CollaborationRoomHistoryFilter(),
    ): Result<CollaborationRoomHistory> {
        if (
            !validCollaborationRoomHistoryEventId(eventId) ||
                limit !in 0..100 ||
                !validCollaborationRoomHistoryFilter(filter)
        )
            return invalidHistory()
        return roomRead(token, correlationId, roomId) { exchange, prepared ->
            exchange.exchange { bearer, deadline ->
                when (val room = prepared.room) {
                    is CollaborationSpaceReadRoom.Published ->
                        providerRoomHistory.context(
                            prepared.owner,
                            room.value,
                            eventId,
                            limit,
                            bearer,
                            deadline,
                            filter,
                        )
                    is CollaborationSpaceReadRoom.Original ->
                        providerRoomHistory.context(
                            prepared.owner,
                            room.value,
                            eventId,
                            limit,
                            bearer,
                            deadline,
                            filter,
                        )
                }
            }
        }
    }

    fun roomEvent(
        token: String,
        correlationId: UUID,
        roomId: String,
        eventId: String,
    ): Result<CollaborationRoomHistory> {
        if (!validCollaborationRoomHistoryEventId(eventId)) return invalidHistory()
        return roomRead(token, correlationId, roomId) { exchange, prepared ->
            readEvent(exchange, prepared, eventId)
        }
    }

    private fun readEvent(
        exchange: Exchange,
        prepared: RoomReadPreparation,
        eventId: String,
    ): Result<CollaborationRoomHistory> =
        exchange.exchange { bearer, deadline ->
            when (val room = prepared.room) {
                is CollaborationSpaceReadRoom.Published ->
                    providerRoomHistory.event(prepared.owner, room.value, eventId, bearer, deadline)
                is CollaborationSpaceReadRoom.Original ->
                    providerRoomHistory.event(prepared.owner, room.value, eventId, bearer, deadline)
            }
        }

    private fun <T> roomRead(
        token: String,
        correlationId: UUID,
        roomId: String,
        operation: (Exchange, RoomReadPreparation) -> Result<T>,
    ): Result<T> {
        if (!validCollaborationSpaceRoomId(roomId))
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_room_request")
            )
        // Owned room metadata is immutable. Peer receiving eligibility is not history authority.
        return admitted(
            token,
            correlationId,
            decrypt = true,
            prepare = { prepareRoomRead(it, roomId) },
            revalidate = { observed, original ->
                prepareRoomRead(observed, roomId).flatMap { current ->
                    if (current == original) Result.Success(Unit)
                    else Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_changed"))
                }
            },
            operation = operation,
        )
    }

    private fun prepareRoomRead(
        observed: Observation,
        roomId: String,
    ): Result<RoomReadPreparation> {
        val device = observed.device
        val owner =
            CollaborationSpaceHistoryOwner(
                device.spaceId,
                device.accountId,
                device.id,
                provider.serverName,
                device.matrixUserId,
                device.matrixDeviceId,
                device.legacy,
            )
        if (!validCollaborationSpaceHistoryIdentity(owner)) return invalidProvider()
        // Only a successful absent group lookup may use separately owned original metadata.
        return rooms.boundRoom(owner.spaceId, owner.accountId, owner.serverName, roomId).flatMap {
            bound ->
            if (bound != null) {
                snapshotCollaborationSpaceBoundRoom(
                        owner.spaceId,
                        owner.accountId,
                        owner.serverName,
                        roomId,
                        bound,
                        clock.instant(),
                    )
                    .flatMap { checked ->
                        if (!validCollaborationSpaceHistoryOwner(owner, checked)) invalidProvider()
                        else
                            Result.Success(
                                RoomReadPreparation(
                                    owner,
                                    CollaborationSpaceReadRoom.Published(checked),
                                )
                            )
                    }
            } else {
                val company =
                    collaborationSpaceHistoryOriginalCompany(owner)
                        ?: return@flatMap Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "collaboration_room_not_found")
                        )
                conversations
                    .legacyBoundRoom(
                        owner.spaceId,
                        owner.accountId,
                        company,
                        owner.serverName,
                        roomId,
                    )
                    .flatMap { original ->
                        if (original == null)
                            Result.Failed(
                                Failure(FailureKind.NOT_FOUND, "collaboration_room_not_found")
                            )
                        else
                            snapshotCollaborationSpaceOriginalReadRoom(
                                    owner,
                                    original,
                                    clock.instant(),
                                )
                                .flatMap { checked ->
                                    if (checked.room?.roomId != roomId) invalidProvider()
                                    else
                                        Result.Success(
                                            RoomReadPreparation(
                                                owner,
                                                CollaborationSpaceReadRoom.Original(checked),
                                            )
                                        )
                                }
                    }
            }
        }
    }

    private data class RoomReadPreparation(
        val owner: CollaborationSpaceHistoryOwner,
        val room: CollaborationSpaceReadRoom,
    )

    private fun invalidHistory() =
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_history_request"))

    fun uploadKeys(
        token: String,
        correlationId: UUID,
        request: CollaborationMatrixKeyUpload,
    ): Result<CollaborationMatrixKeyCounts> {
        val captured = snapshotCollaborationKeyUpload(request)
        if (captured is Result.Failed) return captured
        val upload = (captured as Result.Success).value
        return admitted(token, correlationId, true, { reserveKeys(it, upload) }) {
            exchange,
            reservation ->
            if (upload.deviceKeys == null && !upload.hasSignedKeys)
                return@admitted exchange.exchange { bearer, deadline ->
                    providerKeys.upload(upload, bearer, deadline)
                }
            val retained = reservation ?: return@admitted keysRequired()
            val existing = exchange.readKeys()
            if (existing is Result.Failed) return@admitted existing
            val prior = (existing as Result.Success).value
            if (prior != null) {
                val validated = validatePublicKeys(exchange.observation, retained, prior)
                if (validated is Result.Failed) return@admitted validated
            }
            val identity = upload.deviceKeys ?: prior ?: return@admitted keysRequired()
            val proof = validateCollaborationSignedKeys(upload, identity)
            if (proof is Result.Failed) return@admitted proof
            val response =
                exchange.exchange { bearer, deadline ->
                    providerKeys.upload(upload, bearer, deadline)
                }
            if (response is Result.Failed) return@admitted response
            val readback = exchange.readKeys()
            if (readback is Result.Failed) return@admitted readback
            val published = (readback as Result.Success).value ?: return@admitted invalidProvider()
            validatePublicKeys(exchange.observation, retained, published).flatMap {
                exchange.publishKeys(retained).flatMap { response }
            }
        }
    }

    fun sendRoomMessage(
        token: String,
        correlationId: UUID,
        roomId: String,
        message: CollaborationRoomMessage,
    ): Result<String> {
        val valid = validateCollaborationRoomMessage(message)
        if (valid is Result.Failed) return valid
        if (!validCollaborationSpaceRoomId(roomId))
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_room_message")
            )
        return admitted(
            token,
            correlationId,
            decrypt = true,
            extraAccounts = { head ->
                // Discover guard IDs only; the runner must still admit the caller on failure.
                val lookup = Actor(head.accountId, null, emptySet(), Instant.EPOCH, correlationId)
                transactions.run(lookup) {
                    readBoundRoom(head.spaceId, head.accountId, roomId).map {
                        setOf(it.intent.firstAccountId, it.intent.secondAccountId)
                    }
                }
            },
            prepare = { observed -> prepareRoomMessage(observed, roomId, requireReceiver = true) },
            revalidate = { observed, original ->
                prepareRoomMessage(observed, roomId, requireReceiver = false).flatMap { current ->
                    if (current == original) Result.Success(Unit)
                    else collaborationSpaceRecipientsChanged()
                }
            },
        ) { exchange, prepared ->
            if (
                message.deviceId != null &&
                    message.deviceId != exchange.observation.device.matrixDeviceId
            )
                return@admitted Result.Failed(
                    Failure(FailureKind.CONFLICT, "collaboration_encryption_keys_changed")
                )
            val own = exchange.readConfirmedKeys(prepared.own)
            if (own is Result.Failed) return@admitted own
            if (!collaborationRoomMessageMatchesDevice(message, (own as Result.Success).value))
                return@admitted Result.Failed(
                    Failure(FailureKind.CONFLICT, "collaboration_encryption_keys_changed")
                )
            exchange.exchange { bearer, deadline ->
                providerRoomMessage.send(roomId, message, bearer, deadline)
            }
        }
    }

    private fun readBoundRoom(
        spaceId: UUID,
        accountId: UUID,
        roomId: String,
    ): Result<CollaborationSpaceBoundRoom> =
        rooms.boundRoom(spaceId, accountId, provider.serverName, roomId).flatMap {
            snapshotCollaborationSpaceBoundRoom(
                spaceId,
                accountId,
                provider.serverName,
                roomId,
                it,
                clock.instant(),
            )
        }

    private fun prepareRoomMessage(
        observed: Observation,
        roomId: String,
        requireReceiver: Boolean,
    ): Result<RoomMessagePreparation> =
        readBoundRoom(observed.device.spaceId, observed.actor.accountId, roomId).flatMap { room ->
            val intent = room.intent
            if (
                !observed.relatedAccounts.containsAll(
                    setOf(intent.firstAccountId, intent.secondAccountId)
                )
            )
                return@flatMap invalidProvider()
            val first = intent.firstAccountId == observed.actor.accountId
            val ownUser = if (first) intent.firstMatrixUserId else intent.secondMatrixUserId
            if (ownUser != observed.device.matrixUserId) return@flatMap invalidProvider()
            val peerUser = if (first) intent.secondMatrixUserId else intent.firstMatrixUserId
            val query = CollaborationMatrixKeyQuery(mapOf(peerUser to emptyList()))
            prepareRecipients(observed, query, includeReserved = false).flatMap { selected ->
                if (requireReceiver && selected.providerRequest().devices.isEmpty())
                    return@flatMap Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "collaboration_recipient_unavailable")
                    )
                confirmedOwnKeys(observed).map { RoomMessagePreparation(room, selected, it) }
            }
        }

    private data class RoomMessagePreparation(
        val room: CollaborationSpaceBoundRoom,
        val recipients: CollaborationSpaceKeySelection,
        val own: CollaborationSpaceKeyPublication,
    )

    fun sendToDevice(
        token: String,
        correlationId: UUID,
        request: CollaborationToDevice,
    ): Result<Unit> {
        val captured = snapshotCollaborationSpaceToDevice(request)
        if (captured is Result.Failed) return captured
        val send = (captured as Result.Success).value
        val query = CollaborationMatrixKeyQuery(send.messages.mapValues { it.value.keys.toList() })
        return admitted(
            token,
            correlationId,
            decrypt = true,
            prepare = { observed -> prepareToDevice(observed, query) },
            extraAccounts = { head ->
                collaborationSpaceKeyQueryAccounts(query, head.spaceId, provider.serverName).map {
                    it.values.toSet()
                }
            },
            revalidate = { observed, original ->
                prepareRecipients(observed, query, includeReserved = false).flatMap { current ->
                    if (current != original.recipients)
                        return@flatMap collaborationSpaceRecipientsChanged()
                    confirmedOwnKeys(observed).flatMap { own ->
                        if (own == original.own) Result.Success(Unit) else keysChanged()
                    }
                }
            },
        ) { exchange, prepared ->
            val own = exchange.readConfirmedKeys(prepared.own)
            if (own is Result.Failed) return@admitted own
            val sender = (own as Result.Success).value
            val selected =
                exchange.exchange { bearer, deadline ->
                    providerKeys
                        .deviceKeys(prepared.recipients.providerRequest(), bearer, deadline)
                        .flatMap { verifyCollaborationSpaceKeyQuery(prepared.recipients, it) }
                }
            if (selected is Result.Failed) return@admitted selected
            val identities = (selected as Result.Success).value.deviceKeys
            for ((user, devices) in send.messages) for ((device, envelope) in devices) {
                val receiver =
                    identities[user]?.get(device)
                        ?: return@admitted collaborationSpaceRecipientsChanged()
                if (!collaborationEnvelopeMatchesKeys(envelope, sender, receiver))
                    return@admitted Result.Failed(
                        Failure(FailureKind.CONFLICT, "collaboration_encryption_keys_changed")
                    )
            }
            exchange.exchange { bearer, deadline -> providerToDevice.send(send, bearer, deadline) }
        }
    }

    private fun prepareToDevice(
        observed: Observation,
        query: CollaborationMatrixKeyQuery,
    ): Result<ToDevicePreparation> =
        prepareRecipients(observed, query, includeReserved = false).flatMap { selected ->
            if (
                selected.providerRequest().devices.mapValues { it.value.toSet() } !=
                    query.devices.mapValues { it.value.toSet() }
            )
                return@flatMap Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "collaboration_recipient_unavailable")
                )
            confirmedOwnKeys(observed).map { ToDevicePreparation(selected, it) }
        }

    private fun confirmedOwnKeys(observed: Observation): Result<CollaborationSpaceKeyPublication> {
        val device = observed.device
        return keys.find(device.spaceId, device.accountId, device.id).flatMap { own ->
            if (own == null) return@flatMap keysRequired()
            if (!validPublication(own, observed, clock.instant())) return@flatMap invalidProvider()
            if (own.version == 0L)
                return@flatMap Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_device_keys_pending")
                )
            Result.Success(own)
        }
    }

    private data class ToDevicePreparation(
        val recipients: CollaborationSpaceKeySelection,
        val own: CollaborationSpaceKeyPublication,
    )

    fun queryKeys(
        token: String,
        correlationId: UUID,
        request: CollaborationMatrixKeyQuery,
    ): Result<CollaborationMatrixKeyQueryResult> {
        val captured = snapshotCollaborationSpaceKeyQuery(request)
        if (captured is Result.Failed) return captured
        val query = (captured as Result.Success).value
        return recipientOperation(token, correlationId, query, includeReserved = true) {
            exchange,
            selected ->
            val expanded = selected.providerRequest()
            if (expanded.devices.isEmpty())
                Result.Success(
                    CollaborationMatrixKeyQueryResult(selected.accounts.mapValues { emptyMap() })
                )
            else
                exchange.exchange { bearer, deadline ->
                    providerKeys.deviceKeys(expanded, bearer, deadline).flatMap {
                        verifyCollaborationSpaceKeyQuery(selected, it)
                    }
                }
        }
    }

    fun claimKeys(
        token: String,
        correlationId: UUID,
        request: CollaborationMatrixKeyClaim,
    ): Result<CollaborationMatrixKeyClaimResult> {
        val captured = snapshotCollaborationSpaceKeyClaim(request)
        if (captured is Result.Failed) return captured
        val claim = (captured as Result.Success).value
        val query =
            CollaborationMatrixKeyQuery(claim.oneTimeKeys.mapValues { it.value.keys.toList() })
        return recipientOperation(token, correlationId, query, includeReserved = false) {
            exchange,
            selected ->
            val expanded = selected.providerRequest()
            if (expanded.devices.isEmpty())
                return@recipientOperation Result.Success(
                    CollaborationMatrixKeyClaimResult(selected.accounts.mapValues { emptyMap() })
                )
            val verified =
                exchange.exchange { bearer, deadline ->
                    providerKeys.deviceKeys(expanded, bearer, deadline).flatMap {
                        verifyCollaborationSpaceKeyQuery(selected, it)
                    }
                }
            if (verified is Result.Failed) return@recipientOperation verified
            val exact =
                CollaborationMatrixKeyClaim(
                    expanded.devices.mapValues { (_, devices) ->
                        devices.associateWith { "signed_curve25519" }
                    }
                )
            exchange.exchange { bearer, deadline ->
                providerKeys.claimKeys(exact, bearer, deadline).flatMap {
                    verifyCollaborationSpaceKeyClaim(
                        selected,
                        (verified as Result.Success).value,
                        it,
                    )
                }
            }
        }
    }

    private fun <T> recipientOperation(
        token: String,
        correlationId: UUID,
        query: CollaborationMatrixKeyQuery,
        includeReserved: Boolean,
        operation: (Exchange, CollaborationSpaceKeySelection) -> Result<T>,
    ): Result<T> =
        admitted(
            token,
            correlationId,
            decrypt = true,
            prepare = { observed -> prepareRecipients(observed, query, includeReserved) },
            extraAccounts = { head ->
                collaborationSpaceKeyQueryAccounts(query, head.spaceId, provider.serverName).map {
                    it.values.toSet()
                }
            },
            revalidate = { observed, original ->
                prepareRecipients(observed, query, includeReserved).flatMap { current ->
                    if (current == original) Result.Success(Unit)
                    else collaborationSpaceRecipientsChanged()
                }
            },
            operation = operation,
        )

    private fun prepareRecipients(
        observed: Observation,
        query: CollaborationMatrixKeyQuery,
        includeReserved: Boolean,
    ): Result<CollaborationSpaceKeySelection> {
        val spaceId = observed.device.spaceId
        return collaborationSpaceKeyQueryAccounts(query, spaceId, provider.serverName).flatMap {
            accounts ->
            val at = clock.instant()
            recipients.devices(spaceId, accounts.values.toSet(), at).flatMap { candidates ->
                selectCollaborationSpaceKeys(
                    spaceId,
                    provider.serverName,
                    query,
                    candidates,
                    at,
                    includeReserved,
                )
            }
        }
    }

    private fun reserveKeys(
        observed: Observation,
        upload: CollaborationMatrixKeyUpload,
    ): Result<CollaborationSpaceKeyPublication?> {
        if (upload.deviceKeys == null && !upload.hasSignedKeys) return Result.Success(null)
        val device = observed.device
        val fingerprint =
            upload.deviceKeys?.let {
                validateCollaborationDeviceKeys(it, device.matrixUserId, device.matrixDeviceId)
            }
        if (fingerprint is Result.Failed) return fingerprint
        val candidateFingerprint = (fingerprint as? Result.Success)?.value
        if (
            candidateFingerprint != null &&
                device.legacyKeyFingerprint?.let { it != candidateFingerprint } == true
        )
            return keysChanged()
        val found = keys.find(device.spaceId, device.accountId, device.id)
        if (found is Result.Failed) return found
        val existing = (found as Result.Success).value
        val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
        if (existing != null) {
            if (
                !validPublication(existing, observed, at) ||
                    candidateFingerprint?.let { it != existing.fingerprint } == true
            )
                return keysChanged()
            if (existing.version == 0L && upload.deviceKeys == null && upload.hasSignedKeys)
                return keysRequired()
            return Result.Success(existing)
        }
        if (candidateFingerprint == null) return keysRequired()
        val value =
            CollaborationSpaceKeyPublication(
                device.spaceId,
                device.accountId,
                device.id,
                device.originNativeSessionId,
                device.matrixUserId,
                device.matrixDeviceId,
                candidateFingerprint,
                0,
                at,
            )
        return keys
            .reserve(value)
            .flatMap {
                journal.record(
                    observed.actor,
                    ChangeRecord(
                        "collaboration_space_device",
                        device.id,
                        "collaboration.space_device_keys_reserved",
                        mapOf("spaceId" to device.spaceId.toString(), "version" to "0"),
                    ),
                )
            }
            .flatMap { keys.find(device.spaceId, device.accountId, device.id) }
            .flatMap { if (it == value) Result.Success(value) else keysChanged() }
    }

    private fun validPublication(
        value: CollaborationSpaceKeyPublication,
        observed: Observation,
        at: Instant,
    ): Boolean {
        val d = observed.device
        return validCollaborationSpaceKey(value) &&
            value.spaceId == d.spaceId &&
            value.accountId == d.accountId &&
            value.deviceId == d.id &&
            value.nativeSessionId == d.originNativeSessionId &&
            value.matrixUserId == d.matrixUserId &&
            value.matrixDeviceId == d.matrixDeviceId &&
            value.reservedAt >= d.createdAt &&
            value.reservedAt <= at &&
            value.publishedAt?.let { it > at } != true &&
            d.legacyKeyFingerprint?.let { it != value.fingerprint } != true
    }

    private fun validatePublicKeys(
        observed: Observation,
        retained: CollaborationSpaceKeyPublication,
        value: CollaborationMatrixDeviceKeys,
    ): Result<Unit> =
        when (
            val proof =
                validateCollaborationDeviceKeys(
                    value,
                    observed.device.matrixUserId,
                    observed.device.matrixDeviceId,
                )
        ) {
            is Result.Failed -> invalidProvider()
            is Result.Success ->
                if (proof.value == retained.fingerprint) Result.Success(Unit) else keysChanged()
        }

    private inner class Exchange(
        private val context: Context,
        val observation: Observation,
        val lease: CollaborationSpaceRequestLease,
        private val checkExtra: (() -> Result<Unit>)? = null,
    ) {
        fun publishPusher(retained: CollaborationSpacePushBinding): Result<Unit> =
            transactions.run(context.scope) {
                checkpoint(context, observation, lease, checkExtra)
                    .flatMap {
                        pushBindings.find(
                            context.spaceId,
                            context.scope.accountId,
                            context.deviceId,
                        )
                    }
                    .flatMap publication@{ current ->
                        if (
                            current == null ||
                                !validPushBinding(current, observation) ||
                                !samePushReservation(current, retained)
                        )
                            return@publication pushChanged()
                        if (current.version == 1L)
                            return@publication checkpoint(context, observation, lease, checkExtra)
                        val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
                        val next = current.copy(version = 1L, publishedAt = at)
                        pushBindings
                            .publish(current, at)
                            .flatMap {
                                journal.record(
                                    observation.actor,
                                    ChangeRecord(
                                        "collaboration_space_device",
                                        context.deviceId,
                                        "collaboration.space_push_published",
                                        mapOf(
                                            "spaceId" to context.spaceId.toString(),
                                            "version" to "1",
                                        ),
                                    ),
                                )
                            }
                            .flatMap { checkpoint(context, observation, lease, checkExtra) }
                            .flatMap {
                                pushBindings.find(
                                    context.spaceId,
                                    context.scope.accountId,
                                    context.deviceId,
                                )
                            }
                            .flatMap { readback ->
                                if (readback == next)
                                    checkpoint(context, observation, lease, checkExtra)
                                else pushChanged()
                            }
                    }
            }

        fun <T> exchange(call: (String, Instant) -> Result<T>): Result<T> {
            val before =
                transactions.run(context.scope) {
                    checkpoint(context, observation, lease, checkExtra)
                }
            if (before is Result.Failed) return before
            val response = call(requireNotNull(observation.credential).accessToken, lease.expiresAt)
            if (Thread.currentThread().isInterrupted)
                throw InterruptedException("Group key request interrupted")
            val after =
                transactions.run(context.scope) {
                    checkpoint(context, observation, lease, checkExtra)
                }
            return if (after is Result.Failed) after else response
        }

        fun readConfirmedKeys(
            retained: CollaborationSpaceKeyPublication
        ): Result<CollaborationMatrixDeviceKeys> = exchange { bearer, deadline ->
            val device = observation.device
            providerKeys
                .ownDeviceKeys(device.matrixUserId, device.matrixDeviceId, bearer, deadline)
                .flatMap { keys ->
                    if (keys == null) return@flatMap invalidProvider()
                    val snapshot =
                        snapshotCollaborationKeyUpload(
                            CollaborationMatrixKeyUpload(deviceKeys = keys)
                        )
                    if (snapshot is Result.Failed) return@flatMap invalidProvider()
                    val identity = requireNotNull((snapshot as Result.Success).value.deviceKeys)
                    validatePublicKeys(observation, retained, identity).map { identity }
                }
        }

        fun readKeys(): Result<CollaborationMatrixDeviceKeys?> = exchange { bearer, deadline ->
            providerKeys.ownDeviceKeys(
                observation.device.matrixUserId,
                observation.device.matrixDeviceId,
                bearer,
                deadline,
            )
        }

        fun publishKeys(retained: CollaborationSpaceKeyPublication): Result<Unit> =
            transactions.run(context.scope) {
                checkpoint(context, observation, lease, checkExtra)
                    .flatMap {
                        keys.find(context.spaceId, context.scope.accountId, context.deviceId)
                    }
                    .flatMap publish@{ current ->
                        val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
                        if (
                            current == null ||
                                !validPublication(current, observation, at) ||
                                current.version < retained.version ||
                                (retained.version == 1L && current != retained) ||
                                current.copy(
                                    version = retained.version,
                                    publishedAt = retained.publishedAt,
                                ) != retained
                        )
                            return@publish keysChanged()
                        if (current.version == 1L)
                            return@publish checkpoint(context, observation, lease, checkExtra)
                        val next = retained.copy(version = 1, publishedAt = at)
                        keys
                            .publish(current, at)
                            .flatMap {
                                journal.record(
                                    observation.actor,
                                    ChangeRecord(
                                        "collaboration_space_device",
                                        context.deviceId,
                                        "collaboration.space_device_keys_published",
                                        mapOf(
                                            "spaceId" to context.spaceId.toString(),
                                            "version" to "1",
                                        ),
                                    ),
                                )
                            }
                            .flatMap { checkpoint(context, observation, lease, checkExtra) }
                            .flatMap {
                                keys.find(
                                    context.spaceId,
                                    context.scope.accountId,
                                    context.deviceId,
                                )
                            }
                            .flatMap {
                                if (it == next) checkpoint(context, observation, lease, checkExtra)
                                else keysChanged()
                            }
                    }
            }
    }

    private fun keysChanged() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_device_keys_changed"))

    private fun keysRequired() =
        Result.Failed(Failure(FailureKind.VALIDATION, "collaboration_device_keys_required"))

    private fun invalidProvider() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response"))

    // Only fixed public operations may enter these private runners. No public arbitrary provider
    // callback.
    private fun <T> admitted(
        token: String,
        correlationId: UUID,
        decrypt: Boolean,
        exchange: (Observation, Instant) -> Result<T>,
    ): Result<T> =
        admitted(token, correlationId, decrypt, { Result.Success(Unit) }) { request, _ ->
            exchange(request.observation, request.lease.expiresAt)
        }

    private fun <S, T> admitted(
        token: String,
        correlationId: UUID,
        decrypt: Boolean,
        prepare: (Observation) -> Result<S>,
        extraAccounts: (CollaborationSpaceGatewaySession) -> Result<Set<UUID>> = {
            Result.Success(emptySet())
        },
        revalidate: ((Observation, S) -> Result<Unit>)? = null,
        operation: (Exchange, S) -> Result<T>,
    ): Result<T> {
        if (
            !policy.enabled ||
                !provider.enabled ||
                !validCollaborationSpaceServer(provider.serverName)
        )
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (!validCollaborationSpaceGatewayAccess(token)) return authenticationRequired()
        val started = clock.instant()
        val lookup = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, correlationId)
        val resolved = transactions.run(lookup) { gateway.findAccess(token) }
        if (resolved is Result.Failed) return resolved
        val initial = (resolved as Result.Success).value ?: return authenticationRequired()
        val requestedAccounts = extraAccounts(initial)
        val peers = (requestedAccounts as? Result.Success)?.value ?: emptySet()
        val discoveryFailure =
            when {
                requestedAccounts is Result.Failed -> requestedAccounts
                peers.size > 100 || UUID(0, 0) in peers -> invalidProvider()
                else -> null
            }
        val context =
            Context(
                lookup.copy(accountId = initial.accountId),
                initial,
                started,
                (if (discoveryFailure == null) peers + initial.accountId
                    else setOf(initial.accountId))
                    .sorted(),
            )
        val prepared =
            transactions.run(context.scope) {
                observe(context, decrypt).flatMap { observed ->
                    if (discoveryFailure != null) return@flatMap discoveryFailure
                    val until =
                        minOf(
                                started.plusSeconds(30),
                                observed.admission.expiresAt,
                                initial.accessExpiresAt,
                                initial.expiresAt,
                                observed.state.expiresAt,
                            )
                            .truncatedTo(ChronoUnit.MICROS)
                    requests.claim(position(initial), until).flatMap leaseClaim@{ lease ->
                        if (lease == null)
                            return@leaseClaim Result.Failed(
                                Failure(
                                    FailureKind.UNAVAILABLE,
                                    "collaboration_space_gateway_busy",
                                    parameters = mapOf("retryAfterSeconds" to "1"),
                                )
                            )
                        confirm(context, observed, lease).flatMap {
                            prepare(observed).flatMap { extra ->
                                val checked =
                                    revalidate?.invoke(observed, extra) ?: Result.Success(Unit)
                                checked
                                    .flatMap { confirm(context, observed, lease) }
                                    .map { Preparation(observed, lease, extra) }
                            }
                        }
                    }
                }
            }
        if (prepared is Result.Failed) return prepared
        val value = (prepared as Result.Success).value
        val checkExtra: (() -> Result<Unit>)? =
            revalidate?.let { check -> { check(value.observation, value.extra) } }
        var originalFailure: Throwable? = null
        try {
            val before =
                transactions.run(context.scope) {
                    checkpoint(context, value.observation, value.lease, checkExtra)
                }
            if (before is Result.Failed) return before
            val response =
                operation(
                    Exchange(context, value.observation, value.lease, checkExtra),
                    value.extra,
                )
            if (Thread.currentThread().isInterrupted)
                throw InterruptedException("Group request interrupted")
            val after =
                transactions.run(context.scope) {
                    checkpoint(context, value.observation, value.lease, checkExtra)
                }
            return if (after is Result.Failed) after else response
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            if (!Thread.currentThread().isInterrupted) {
                try {
                    transactions.run(context.scope) { requests.release(value.lease) }
                } catch (cleanup: Throwable) {
                    val original = originalFailure
                    if (original == null) throw cleanup
                    if (original !== cleanup) original.addSuppressed(cleanup)
                }
            }
        }
    }

    private fun checkpoint(
        context: Context,
        original: Observation,
        lease: CollaborationSpaceRequestLease,
        extra: (() -> Result<Unit>)?,
    ): Result<Unit> =
        confirm(context, original, lease).flatMap {
            if (extra == null) Result.Success(Unit)
            else extra().flatMap { confirm(context, original, lease) }
        }

    private fun confirm(
        context: Context,
        original: Observation,
        lease: CollaborationSpaceRequestLease,
    ): Result<Unit> =
        observe(context, decrypt = false).flatMap { current ->
            if (
                current.device != original.device ||
                    current.identity != original.identity ||
                    current.state != original.state ||
                    current.actor != original.actor ||
                    current.admission.nativeSessionVersion !=
                        original.admission.nativeSessionVersion ||
                    current.admission.credentialVersion != original.admission.credentialVersion
            )
                return@flatMap changed()
            requests.find(position(context.initial).owner, lease.slot, lock = true).flatMap { actual
                ->
                val at = clock.instant()
                if (
                    actual != lease ||
                        lease.position != position(context.initial) ||
                        lease.slot !in 1..7 ||
                        lease.token == UUID(0, 0) ||
                        lease.startedAt > at ||
                        lease.expiresAt >
                            minOf(
                                context.started.plusSeconds(30),
                                original.admission.expiresAt,
                                context.initial.accessExpiresAt,
                                context.initial.expiresAt,
                                original.state.expiresAt,
                            ) ||
                        at < original.admission.admittedAt ||
                        at < current.admission.admittedAt ||
                        at >= minOf(lease.expiresAt, current.admission.expiresAt)
                )
                    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_request_lost"))
                else Result.Success(Unit)
            }
        }

    private fun position(head: CollaborationSpaceGatewaySession) =
        CollaborationSpaceRequestPosition(
            CollaborationSpaceRequestOwner(
                head.spaceId,
                head.accountId,
                head.deviceId,
                head.nativeSessionId,
            ),
            CollaborationSpaceRequestKind.GATEWAY,
            head.id,
            head.version,
        )

    /**
     * Sorted accounts -> native -> device -> gateway -> request; provider I/O is always outside.
     */
    private fun observe(context: Context, decrypt: Boolean): Result<Observation> {
        for (account in context.accounts) {
            val guarded = identities.lockAccount(account, shared = true)
            if (guarded is Result.Failed) return guarded
        }
        val locked = sessions.lock(context.parentId, context.scope.accountId)
        if (locked is Result.Failed) return locked
        val parent = (locked as Result.Success).value ?: return nativeAuthenticationRequired()
        val actor =
            context.scope.copy(
                authenticatedAt = parent.authenticatedAt,
                mfaVerifiedAt = parent.mfaVerifiedAt,
                credentialVersion = parent.credentialVersion,
            )

        val found =
            registry.metadata(context.spaceId, actor.accountId, context.deviceId, lock = true)
        if (found is Result.Failed) return found
        val device = (found as Result.Success).value ?: return denied()
        val bound = registry.findBound(context.spaceId, actor.accountId, context.parentId)
        if (bound is Result.Failed) return bound
        if (
            (bound as Result.Success).value != device ||
                device.id != context.deviceId ||
                device.spaceId != context.spaceId ||
                device.accountId != actor.accountId ||
                device.originNativeSessionId != context.parentId ||
                device.platform.name != context.client.platform.name ||
                device.state != CollaborationDeviceState.ACTIVE ||
                device.revokedAt != null
        )
            return denied()
        val head = gateway.find(context.spaceId, actor.accountId, context.deviceId, lock = true)
        if (head is Result.Failed) return head
        if ((head as Result.Success).value != context.initial) return authenticationRequired()
        if (context.initial.expiresAt > parent.expiresAt) return authenticationRequired()
        fun admission(): Result<CollaborationSpaceAdmission> {
            val access = identities.access(actor.accountId, null)
            if (access is Result.Failed) return access
            val current = spaces.current()
            if (current is Result.Failed) return current
            val now = clock.instant()
            if (now < context.started || now >= context.started.plusSeconds(30)) return changed()
            return admitCollaborationSpaceSession(
                    actor,
                    (access as Result.Success).value,
                    parent,
                    context.parentId,
                    parent.version,
                    (current as Result.Success).value,
                    context.client,
                    policy,
                    security,
                    now,
                )
                .flatMap { if (it.spaceId == context.spaceId) Result.Success(it) else denied() }
        }
        val admitted = admission()
        if (admitted is Result.Failed) return admitted
        val scope = (admitted as Result.Success).value
        val gatewayValid =
            validateCollaborationSpaceGatewaySession(
                context.initial,
                scope,
                device,
                clock.instant(),
            )
        if (gatewayValid is Result.Failed) return gatewayValid
        if (clock.instant() >= context.initial.accessExpiresAt) return authenticationRequired()

        val input = selections.selection(context.spaceId, actor.accountId, provider.serverName)
        if (input is Result.Failed) return input
        val selection = (input as Result.Success).value ?: return denied()
        if (selection.selected == null) return denied()
        val selected =
            selectCollaborationSpaceIdentity(
                context.spaceId,
                actor.accountId,
                provider.serverName,
                selection,
                clock.instant(),
            )
        if (selected is Result.Failed) return selected
        val identity = (selected as Result.Success).value
        val valid =
            validateExistingCollaborationSpaceDevice(
                identity,
                device,
                context.parentId,
                device.platform,
                device.origin,
                device.legacy,
                device.legacyKeyFingerprint,
                clock.instant(),
            )
        if (valid is Result.Failed) return valid
        val read = providers.metadata(context.spaceId, actor.accountId, context.deviceId)
        if (read is Result.Failed) return read
        val state = (read as Result.Success).value ?: return denied()
        val live = validateCollaborationSpaceGatewayProvider(state, device, scope, clock.instant())
        if (live is Result.Failed) return live
        if (device.origin == CollaborationSpaceDeviceOrigin.LEGACY) {
            val legacy = device.legacy ?: return denied()
            val source =
                legacyParents.find(context.spaceId, actor.accountId, context.parentId, legacy)
            if (source is Result.Failed) return source
            val completed =
                completions.find(
                    CollaborationLegacyRuntimeOwner(
                        legacy.companyId,
                        legacy.deviceId,
                        actor.accountId,
                        context.parentId,
                    )
                )
            if (completed is Result.Failed) return completed
            val done = (completed as Result.Success).value ?: return denied()
            val queue = provisioning.metadata(context.spaceId, actor.accountId, context.deviceId)
            if (queue is Result.Failed) return queue
            val continuity =
                validateCompletedCollaborationLegacyDevice(
                    identity,
                    device,
                    (source as Result.Success).value,
                    done,
                    (queue as Result.Success).value,
                    scope,
                    clock.instant(),
                )
            if (continuity is Result.Failed) return continuity
            if (
                state.generation < done.targetProviderGeneration ||
                    state.createdAt > done.completedAt
            )
                return denied()
        }
        val credential =
            if (decrypt) {
                val acquired = providers.credential(state)
                if (acquired is Result.Failed) return acquired
                val value = (acquired as Result.Success).value
                if (
                    value.state != state ||
                        value.accessToken.length !in 1..2048 ||
                        value.accessToken.any { it.code !in 33..126 }
                )
                    return authenticationRequired()
                value
            } else null
        return admission().flatMap { final ->
            val now = clock.instant()
            if (now >= context.initial.accessExpiresAt || now >= state.expiresAt)
                authenticationRequired()
            else
                validateCollaborationSpaceGatewaySession(context.initial, final, device, now).map {
                    Observation(
                        actor,
                        device,
                        identity,
                        state,
                        credential,
                        final,
                        context.accounts.toSet(),
                    )
                }
        }
    }

    private fun authenticationRequired() = collaborationSpaceGatewayAuthenticationRequired()

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private class Context(
        val scope: Actor,
        val initial: CollaborationSpaceGatewaySession,
        val started: Instant,
        val accounts: List<UUID>,
    ) {
        val spaceId
            get() = initial.spaceId

        val parentId
            get() = initial.nativeSessionId

        val deviceId
            get() = initial.deviceId

        val client =
            ClientVersion(ClientPlatform.valueOf(initial.platform.name), initial.clientBuild)
    }

    private class Observation(
        val actor: Actor,
        val device: CollaborationSpaceDevice,
        val identity: CollaborationSpaceIdentity,
        val state: CollaborationSpaceProviderState,
        val credential: CollaborationSpaceProviderSession?,
        val admission: CollaborationSpaceAdmission,
        val relatedAccounts: Set<UUID>,
    )

    private class Preparation<S>(
        val observation: Observation,
        val lease: CollaborationSpaceRequestLease,
        val extra: S,
    )
}
