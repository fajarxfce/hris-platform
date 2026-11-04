package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** One claimed room attempt. Provider calls never hold SQL transactions or SDK sync cursors. */
class AdvanceCollaborationSpaceRoom(
    private val rooms: CollaborationSpaceRoomRepository,
    private val conversations: CollaborationSpaceConversationRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val providers: CollaborationSpaceProviderSessionRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val requests: CollaborationSpaceRequestRepository,
    private val matrix: CollaborationSpaceMatrixRoomRepository,
    private val originalRooms: CollaborationMatrixRoomRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(lease: CollaborationSpaceRoomLease): Result<Unit> {
        val available = validateCollaborationSpaceClient(lease.request.client, policy)
        if (available is Result.Failed) return available
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val request = lease.request
        val actor =
            Actor(
                request.accountId,
                null,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
                mfaVerifiedAt = request.mfaVerifiedAt,
                credentialVersion = request.credentialVersion,
            )
        val context = Context(actor, lease, clock.instant())
        val prepared = transactions.run(actor) { prepare(context) }
        if (prepared is Result.Failed) return prepared
        val value = (prepared as Result.Success).value
        var originalFailure: Throwable? = null
        try {
            val found =
                when (value.observation.intent.origin) {
                    CollaborationSpaceRoomOrigin.SPACE -> reconcileSpace(context, value)
                    CollaborationSpaceRoomOrigin.LEGACY -> reconcileOriginal(context, value)
                }
            if (found is Result.Failed) return found
            val resolved = (found as Result.Success).value
            return transactions.run(actor) {
                confirm(context, value.observation, value.slot).flatMap {
                    val binding =
                        CollaborationSpaceRoomBinding(
                            request.spaceId,
                            request.conversationId,
                            request.accountId,
                            request.revision,
                            lease.token,
                            resolved.id,
                            clock.instant().truncatedTo(ChronoUnit.MICROS),
                            resolved.creator,
                        )
                    rooms
                        .publish(binding)
                        .flatMap { rooms.finish(lease) }
                        .flatMap { if (it) Result.Success(Unit) else lostLease() }
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "collaboration_space_room",
                                    request.conversationId,
                                    "collaboration.space_room_prepared",
                                    mapOf(
                                        "spaceId" to request.spaceId.toString(),
                                        "requestRevision" to request.revision.toString(),
                                    ),
                                ),
                            )
                        }
                        .flatMap { confirm(context, value.observation, value.slot, binding) }
                }
            }
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            // On interruption only the finite slot remains. Never logout a retained device.
            if (!Thread.currentThread().isInterrupted) {
                try {
                    transactions.run(actor) { requests.release(value.slot) }
                } catch (cleanup: Throwable) {
                    val initial = originalFailure
                    if (initial == null) throw cleanup
                    if (initial !== cleanup) initial.addSuppressed(cleanup)
                }
            }
        }
    }

    private fun prepare(context: Context): Result<Preparation> =
        observe(context).flatMap observed@{ before ->
            val until =
                minOf(
                        context.started.plusSeconds(30),
                        context.lease.until,
                        context.lease.request.expiresAt,
                        before.scope.expiresAt,
                        before.provider.expiresAt,
                    )
                    .truncatedTo(ChronoUnit.MICROS)
            if (until <= clock.instant()) return@observed lostLease()
            requests.claim(position(context), until).flatMap claimed@{ slot ->
                if (slot == null)
                    return@claimed Result.Failed(
                        Failure(
                            FailureKind.UNAVAILABLE,
                            "collaboration_space_request_busy",
                            parameters = mapOf("retryAfterSeconds" to "2"),
                        )
                    )
                confirm(context, before, slot).flatMap {
                    val acquired = providers.credential(before.provider)
                    confirm(context, before, slot).flatMap current@{
                        if (acquired is Result.Failed) return@current acquired
                        val credential = (acquired as Result.Success).value
                        if (credential.state != before.provider) changed()
                        else Result.Success(Preparation(before, slot, credential.accessToken))
                    }
                }
            }
        }

    private fun confirm(
        context: Context,
        before: Observation,
        slot: CollaborationSpaceRequestLease,
        published: CollaborationSpaceRoomBinding? = null,
    ): Result<Unit> =
        observe(context, published).flatMap { current ->
            if (
                current.device != before.device ||
                    current.provider != before.provider ||
                    !samePair(current.pair, before.pair) ||
                    current.intent != before.intent ||
                    current.scope.nativeSessionVersion != before.scope.nativeSessionVersion ||
                    current.scope.credentialVersion != before.scope.credentialVersion
            )
                return@flatMap changed()
            requests.find(position(context).owner, slot.slot, lock = true).flatMap { stored ->
                val now = clock.instant()
                if (
                    stored != slot ||
                        slot.position != position(context) ||
                        slot.slot !in 1..7 ||
                        slot.token == UUID(0, 0) ||
                        slot.startedAt > now ||
                        slot.expiresAt >
                            minOf(
                                context.started.plusSeconds(30),
                                context.lease.until,
                                context.lease.request.expiresAt,
                                before.scope.expiresAt,
                                before.provider.expiresAt,
                            ) ||
                        now < context.started ||
                        now >=
                            minOf(
                                slot.expiresAt,
                                current.scope.expiresAt,
                                current.provider.expiresAt,
                            )
                )
                    lostLease()
                else Result.Success(Unit)
            }
        }

    private fun <T> exchange(
        context: Context,
        prepared: Preparation,
        operation: () -> Result<T>,
    ): Result<T> {
        checkInterrupted()
        val before =
            transactions.run(context.actor) {
                confirm(context, prepared.observation, prepared.slot)
            }
        if (before is Result.Failed) return before
        val response = operation()
        checkInterrupted()
        val after =
            transactions.run(context.actor) {
                confirm(context, prepared.observation, prepared.slot)
            }
        return if (after is Result.Failed) after else response
    }

    private fun reconcileSpace(context: Context, prepared: Preparation): Result<ResolvedRoom> {
        val intent = prepared.observation.intent
        val callerFirst = context.actor.accountId == intent.firstAccountId
        val requested =
            CollaborationSpaceMatrixDirectRoom(
                intent.spaceId,
                intent.conversationId,
                if (callerFirst) intent.firstAccountId else intent.secondAccountId,
                if (callerFirst) intent.secondAccountId else intent.firstAccountId,
                if (callerFirst) intent.firstMatrixUserId else intent.secondMatrixUserId,
                if (callerFirst) intent.secondMatrixUserId else intent.firstMatrixUserId,
            )
        val token = prepared.token
        val deadline = prepared.slot.expiresAt
        val initial = exchange(context, prepared) { matrix.resolve(requested, token, deadline) }
        if (initial is Result.Failed) return initial
        var room = (initial as Result.Success).value
        if (room == null) {
            val created = exchange(context, prepared) { matrix.create(requested, token, deadline) }
            // Even after a lost/malformed response, resolve this one immutable alias exactly once.
            val recovered =
                exchange(context, prepared) { matrix.resolve(requested, token, deadline) }
            if (recovered is Result.Failed) return recovered
            room = (recovered as Result.Success).value
            if (room == null)
                return if (created is Result.Failed) created
                else
                    Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
                    )
            val returned =
                (created as? Result.Success)?.value as? CollaborationMatrixRoomCreation.Created
            if (returned != null && returned.roomId != room) return mismatch()
        }
        val id = room
        val joined = exchange(context, prepared) { matrix.join(id, token, deadline) }
        if (joined is Result.Failed) return joined
        val observed = exchange(context, prepared) { matrix.observe(id, token, deadline) }
        if (observed is Result.Failed) return observed
        val assessed =
            assessCollaborationSpaceRecoveredRoom(
                requested,
                (observed as Result.Success).value,
                provider.serverName,
            )
        if (assessed is Result.Failed) return assessed
        val assessment = (assessed as Result.Success).value
        val repair = assessment.repair
        for (setting in repair.missingSettings) {
            val written =
                exchange(context, prepared) {
                    matrix.putInitialSetting(id, setting, token, deadline)
                }
            if (written is Result.Failed) return written
        }
        if (repair.missingInvite) {
            val invited =
                exchange(context, prepared) { matrix.invitePeer(id, requested, token, deadline) }
            if (invited is Result.Failed) return invited
        }
        if (repair.missingSettings.isNotEmpty() || repair.missingInvite) {
            val checked = exchange(context, prepared) { matrix.observe(id, token, deadline) }
            if (checked is Result.Failed) return checked
            val final =
                assessCollaborationSpaceRecoveredRoom(
                    requested,
                    (checked as Result.Success).value,
                    provider.serverName,
                )
            if (final is Result.Failed) return final
            val value = (final as Result.Success).value
            if (
                value.creatorAccountId != assessment.creatorAccountId ||
                    value.repair.missingSettings.isNotEmpty() ||
                    value.repair.missingInvite
            )
                return mismatch()
        }
        return Result.Success(ResolvedRoom(id, assessment.creatorAccountId))
    }

    private fun reconcileOriginal(context: Context, prepared: Preparation): Result<ResolvedRoom> {
        val intent = prepared.observation.intent
        val original = intent.original ?: return mismatch()
        val room =
            CollaborationOwnedRoom(
                original.companyId,
                original.conversationId,
                intent.firstAccountId,
                intent.secondAccountId,
                intent.matrixCreatorAccountId,
                intent.serverName,
                original.roomId,
            )
        val token = prepared.token
        val deadline = prepared.slot.expiresAt
        val joined = exchange(context, prepared) { matrix.join(room.roomId, token, deadline) }
        if (joined is Result.Failed) return joined
        val observed =
            exchange(context, prepared) { originalRooms.observe(room.roomId, token, deadline) }
        if (observed is Result.Failed) return observed
        val current = (observed as Result.Success).value
        if (current.members[prepared.observation.device.matrixUserId] != "join") return mismatch()
        return assessCollaborationSpaceLegacyRoom(room, current).map {
            ResolvedRoom(room.roomId, room.creatorAccountId)
        }
    }

    private fun observe(
        context: Context,
        published: CollaborationSpaceRoomBinding? = null,
    ): Result<Observation> {
        val request = context.lease.request
        val actor = context.actor
        val spaceId = request.spaceId
        val conversationId = request.conversationId
        val deviceId = request.deviceId
        val expectedDeviceVersion = request.deviceVersion
        val parentId = request.nativeSessionId
        val parentVersion = request.nativeSessionVersion
        val currentClient = request.client
        val started = context.started
        val guard = rooms.lock(spaceId, conversationId)
        if (guard is Result.Failed) return guard
        val readConversation = conversations.find(spaceId, actor.accountId, conversationId)
        if (readConversation is Result.Failed) return readConversation
        val conversation = (readConversation as Result.Success).value ?: return notFound()
        fun participants(): Result<CollaborationSpaceRoomParticipants> {
            val first = conversations.member(spaceId, conversation.firstAccountId)
            if (first is Result.Failed) return first
            val second = conversations.member(spaceId, conversation.secondAccountId)
            if (second is Result.Failed) return second
            return prepareCollaborationSpaceRoomParticipants(
                conversation,
                (first as Result.Success).value,
                (second as Result.Success).value,
                provider.serverName,
                clock.instant().truncatedTo(ChronoUnit.MICROS),
            )
        }
        val readPair = participants()
        if (readPair is Result.Failed) return readPair
        val initialPair = (readPair as Result.Success).value
        val originalCompany = initialPair.originalCompanyId
        if (originalCompany != null) {
            val originalGuard =
                rooms.lockOriginalPair(
                    originalCompany,
                    conversation.firstAccountId,
                    conversation.secondAccountId,
                )
            if (originalGuard is Result.Failed) return originalGuard
        }
        for (account in
            listOf(conversation.firstAccountId, conversation.secondAccountId).sorted()) {
            val accountGuard = identities.lockAccount(account, shared = true)
            if (accountGuard is Result.Failed) return accountGuard
        }
        val locked = sessions.lock(parentId, actor.accountId)
        if (locked is Result.Failed) return locked
        val parent = (locked as Result.Success).value ?: return nativeAuthenticationRequired()
        val readDevice = registry.metadata(spaceId, actor.accountId, deviceId, lock = true)
        if (readDevice is Result.Failed) return readDevice
        val device = (readDevice as Result.Success).value ?: return denied()
        val boundDevice = registry.findBound(spaceId, actor.accountId, parentId)
        if (boundDevice is Result.Failed) return boundDevice
        if (
            (boundDevice as Result.Success).value != device ||
                device.id != deviceId ||
                device.spaceId != spaceId ||
                device.accountId != actor.accountId ||
                device.originNativeSessionId != parentId ||
                device.platform.name != currentClient.platform.name ||
                device.state != CollaborationDeviceState.ACTIVE ||
                device.revokedAt != null ||
                device.version != expectedDeviceVersion
        )
            return denied()
        val readHead = rooms.provisioning(spaceId, actor.accountId, conversationId, lock = true)
        if (readHead is Result.Failed) return readHead
        val head = (readHead as Result.Success).value
        if (head == null || head.request != request || head.attempts != context.lease.attempt)
            return lostLease()
        val exact =
            if (published == null)
                head.lease == context.lease &&
                    head.state == CollaborationSpaceProvisioningState.RUNNING
            else
                head.lease == null &&
                    head.state == CollaborationSpaceProvisioningState.SUCCEEDED &&
                    head.lastOutcome?.revision == request.revision &&
                    head.lastOutcome.state == CollaborationSpaceProvisioningState.SUCCEEDED &&
                    head.lastOutcome.failure == null &&
                    head.lastOutcome.at >= published.createdAt &&
                    published.leaseToken == context.lease.token &&
                    published.publishedBy == request.accountId
        if (!exact) return lostLease()
        val readIntent = rooms.intent(spaceId, actor.accountId, conversationId)
        if (readIntent is Result.Failed) return readIntent
        val intent = (readIntent as Result.Success).value ?: return changed()
        if (
            intent.spaceId != spaceId ||
                intent.conversationId != conversationId ||
                intent.firstAccountId != conversation.firstAccountId ||
                intent.secondAccountId != conversation.secondAccountId ||
                intent.firstMatrixUserId != initialPair.first.identity?.userId ||
                intent.secondMatrixUserId != initialPair.second.identity?.userId ||
                intent.serverName != provider.serverName
        )
            return changed()
        val canonicalPair = setOf(intent.firstAccountId, intent.secondAccountId)
        if (
            actor.accountId !in canonicalPair ||
                intent.requestedBy !in canonicalPair ||
                intent.matrixCreatorAccountId !in canonicalPair ||
                intent.createdAt > request.requestedAt ||
                (intent.origin == CollaborationSpaceRoomOrigin.LEGACY) !=
                    (intent.original != null) ||
                (intent.original != null &&
                    intent.original.companyId != initialPair.originalCompanyId)
        )
            return changed()
        val binding = rooms.binding(spaceId, actor.accountId, conversationId)
        if (binding is Result.Failed) return binding
        if ((binding as Result.Success).value != published) return lostLease()
        fun currentWindow(): Boolean {
            val now = clock.instant()
            return now >= started &&
                now >= request.requestedAt &&
                now < started.plusSeconds(30) &&
                now < request.expiresAt &&
                now < context.lease.until
        }
        fun nativeAdmission(): Result<CollaborationSpaceAdmission> {
            val acquired = identities.access(actor.accountId, null)
            if (acquired is Result.Failed) return acquired
            val current = spaces.current()
            if (current is Result.Failed) return current
            val at = clock.instant()
            if (!currentWindow()) return lostLease()
            val admitted =
                admitCollaborationSpaceSession(
                    actor,
                    (acquired as Result.Success).value,
                    parent,
                    parentId,
                    parentVersion,
                    (current as Result.Success).value,
                    currentClient,
                    policy,
                    security,
                    at,
                )
            if (admitted is Result.Failed) return admitted
            val scope = (admitted as Result.Success).value
            if (scope.spaceId != spaceId) return denied()
            return Result.Success(scope)
        }
        fun admission(): Result<Observation> {
            val admitted = nativeAdmission()
            if (admitted is Result.Failed) return admitted
            val scope = (admitted as Result.Success).value
            val at = clock.instant()
            val input = selections.selection(spaceId, actor.accountId, provider.serverName)
            if (input is Result.Failed) return input
            val selection = (input as Result.Success).value ?: return denied()
            if (selection.selected == null) return denied()
            val selected =
                selectCollaborationSpaceIdentity(
                    spaceId,
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
                    parentId,
                    device.platform,
                    device.origin,
                    device.legacy,
                    device.legacyKeyFingerprint,
                    clock.instant(),
                )
            if (valid is Result.Failed) return valid
            val read = providers.metadata(spaceId, actor.accountId, deviceId)
            if (read is Result.Failed) return read
            val state = (read as Result.Success).value
            val live =
                validateCollaborationSpaceGatewayProvider(state, device, scope, clock.instant())
            if (live is Result.Failed) return live
            if (requireNotNull(state).generation != request.providerGeneration) return changed()
            if (device.origin == CollaborationSpaceDeviceOrigin.LEGACY) {
                val legacy = device.legacy ?: return denied()
                val source = legacyParents.find(spaceId, actor.accountId, parentId, legacy)
                if (source is Result.Failed) return source
                val completed =
                    completions.find(
                        CollaborationLegacyRuntimeOwner(
                            legacy.companyId,
                            legacy.deviceId,
                            actor.accountId,
                            parentId,
                        )
                    )
                if (completed is Result.Failed) return completed
                val done = (completed as Result.Success).value ?: return denied()
                val queue = provisioning.metadata(spaceId, actor.accountId, deviceId)
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
                    requireNotNull(state).generation < done.targetProviderGeneration ||
                        state.createdAt > done.completedAt
                )
                    return denied()
            }
            val finished = clock.instant()
            if (
                finished < at ||
                    finished >= started.plusSeconds(30) ||
                    finished >= scope.expiresAt ||
                    finished >= requireNotNull(state).expiresAt
            )
                return observationChanged()
            val public = participants()
            val finalNative = nativeAdmission()
            if (finalNative is Result.Failed) return finalNative
            if (public is Result.Failed) return public
            if (!samePair(initialPair, (public as Result.Success).value))
                return observationChanged()
            val finalScope = (finalNative as Result.Success).value
            val finalAt = clock.instant()
            if (
                finalAt < at ||
                    finalAt >= started.plusSeconds(30) ||
                    finalAt >= finalScope.expiresAt ||
                    finalAt >= requireNotNull(state).expiresAt
            )
                return observationChanged()
            if (!currentWindow()) return lostLease()
            return Result.Success(
                Observation(finalScope, requireNotNull(state), device, initialPair, intent)
            )
        }
        return admission()
    }

    private fun position(context: Context) =
        CollaborationSpaceRequestPosition(
            CollaborationSpaceRequestOwner(
                context.lease.request.spaceId,
                context.actor.accountId,
                context.lease.request.deviceId,
                context.lease.request.nativeSessionId,
            ),
            CollaborationSpaceRequestKind.ROOM_PREPARATION,
        )

    private fun samePair(
        a: CollaborationSpaceRoomParticipants,
        b: CollaborationSpaceRoomParticipants,
    ) =
        a.conversation == b.conversation &&
            a.originalCompanyId == b.originalCompanyId &&
            a.serverName == b.serverName &&
            a.first.accountId == b.first.accountId &&
            a.second.accountId == b.second.accountId &&
            a.first.identity == b.first.identity &&
            a.second.identity == b.second.identity

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException("Group room operation interrupted")
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))

    private fun observationChanged() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_observation_changed"))

    private fun lostLease() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_lease_lost"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private fun notFound() =
        Result.Failed(Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found"))

    private fun mismatch() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_mismatch"))

    private class Context(
        val actor: Actor,
        val lease: CollaborationSpaceRoomLease,
        val started: Instant,
    )

    private class Observation(
        val scope: CollaborationSpaceAdmission,
        val provider: CollaborationSpaceProviderState,
        val device: CollaborationSpaceDevice,
        val pair: CollaborationSpaceRoomParticipants,
        val intent: CollaborationSpaceRoomIntent,
    )

    private class Preparation(
        val observation: Observation,
        val slot: CollaborationSpaceRequestLease,
        val token: String,
    ) {
        override fun toString() = "RoomPreparation(<redacted>)"
    }

    private class ResolvedRoom(val id: String, val creator: UUID)
}
