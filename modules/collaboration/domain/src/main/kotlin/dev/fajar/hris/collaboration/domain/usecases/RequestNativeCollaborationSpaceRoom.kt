package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Native request/explicit retry only; provider work belongs to the separately admitted worker. */
class RequestNativeCollaborationSpaceRoom(
    private val rooms: CollaborationSpaceRoomRepository,
    private val conversations: CollaborationSpaceConversationRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val providers: CollaborationSpaceProviderSessionRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        parentId: UUID,
        parentVersion: Long,
        spaceId: UUID,
        operationId: UUID,
        conversationId: UUID,
        deviceId: UUID,
        expectedDeviceVersion: Long,
        expectedRequestRevision: Long?,
        client: ClientVersion?,
        expectedRequestAccountId: UUID? = null,
    ): Result<MutationReceipt> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (
            listOf(parentId, spaceId, operationId, conversationId, deviceId).any {
                it == UUID(0, 0)
            } ||
                parentVersion < 0 ||
                expectedDeviceVersion !in 0..999999 ||
                expectedRequestRevision?.let { it !in 0..999999 } == true ||
                ((expectedRequestRevision == null) != (expectedRequestAccountId == null)) ||
                expectedRequestAccountId == UUID(0, 0)
        )
            return changed()
        val currentClient = requireNotNull(client)
        val started = clock.instant()
        val key =
            OperationKey(
                "collaboration.space_room_setup",
                operationId,
                listOf(
                    spaceId.toString(),
                    conversationId.toString(),
                    parentId.toString(),
                    parentVersion.toString(),
                    deviceId.toString(),
                    expectedDeviceVersion.toString(),
                    expectedRequestRevision?.toString() ?: "initial",
                    expectedRequestAccountId?.toString() ?: "initial",
                    currentClient.platform.name,
                    currentClient.build.toString(),
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = rooms.lock(spaceId, conversationId)
            if (guard is Result.Failed) return@run guard
            val readConversation = conversations.find(spaceId, actor.accountId, conversationId)
            if (readConversation is Result.Failed) return@run readConversation
            val conversation = (readConversation as Result.Success).value ?: return@run notFound()
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
            if (readPair is Result.Failed) return@run readPair
            val initialPair = (readPair as Result.Success).value
            val originalCompany = initialPair.originalCompanyId
            if (originalCompany != null) {
                val originalGuard =
                    rooms.lockOriginalPair(
                        originalCompany,
                        conversation.firstAccountId,
                        conversation.secondAccountId,
                    )
                if (originalGuard is Result.Failed) return@run originalGuard
            }
            for (account in
                listOf(conversation.firstAccountId, conversation.secondAccountId).sorted()) {
                val accountGuard = identities.lockAccount(account, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val locked = sessions.lock(parentId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent =
                (locked as Result.Success).value ?: return@run nativeAuthenticationRequired()
            val readDevice = registry.metadata(spaceId, actor.accountId, deviceId, lock = true)
            if (readDevice is Result.Failed) return@run readDevice
            val device = (readDevice as Result.Success).value ?: return@run denied()
            val boundDevice = registry.findBound(spaceId, actor.accountId, parentId)
            if (boundDevice is Result.Failed) return@run boundDevice
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
                return@run denied()
            fun nativeAdmission(): Result<CollaborationSpaceAdmission> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val at = clock.instant()
                if (at < started || at >= started.plusSeconds(30)) return observationChanged()
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
                return Result.Success(Observation(finalScope, requireNotNull(state)))
            }

            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val initial = (admitted as Result.Success).value
            fun finalAdmission(): Result<Unit> =
                admission().flatMap {
                    if (it.provider != initial.provider) observationChanged()
                    else Result.Success(Unit)
                }
            val readIntent = rooms.intent(spaceId, actor.accountId, conversationId)
            if (readIntent is Result.Failed) return@run readIntent
            var intent = (readIntent as Result.Success).value
            if (
                intent != null &&
                    (intent.spaceId != spaceId ||
                        intent.conversationId != conversationId ||
                        intent.firstAccountId != conversation.firstAccountId ||
                        intent.secondAccountId != conversation.secondAccountId ||
                        intent.firstMatrixUserId != initialPair.first.identity?.userId ||
                        intent.secondMatrixUserId != initialPair.second.identity?.userId ||
                        intent.serverName != provider.serverName)
            )
                return@run changed()
            val readStatus = rooms.status(spaceId, actor.accountId, conversationId)
            if (readStatus is Result.Failed) return@run readStatus
            val status = (readStatus as Result.Success).value
            if (
                status != null &&
                    (status.spaceId != spaceId ||
                        status.conversationId != conversationId ||
                        status.accountId !in
                            setOf(conversation.firstAccountId, conversation.secondAccountId))
            )
                return@run changed()
            val previous = (replay as Result.Success).value
            if (previous != null) {
                if (intent == null || status == null || previous.id != conversationId)
                    return@run changed()
                return@run finalAdmission().map { previous }
            }
            if (status != null && expectedRequestRevision == null) {
                if (intent == null) return@run changed()
                val receipt = MutationReceipt(conversationId, status.revision)
                return@run operations.record(actor, key, receipt).flatMap {
                    finalAdmission().map { receipt }
                }
            }
            if ((intent == null) != (status == null)) return@run changed()
            if (
                expectedRequestRevision != null &&
                    (status == null ||
                        status.accountId != expectedRequestAccountId ||
                        status.revision != expectedRequestRevision ||
                        status.state !in
                            setOf(
                                CollaborationSpaceProvisioningState.FAILED,
                                CollaborationSpaceProvisioningState.CANCELLED,
                            ))
            )
                return@run changed()
            val bound = rooms.binding(spaceId, actor.accountId, conversationId)
            if (bound is Result.Failed) return@run bound
            if ((bound as Result.Success).value != null) return@run changed()
            val owned = rooms.provisioning(spaceId, actor.accountId, conversationId, lock = true)
            if (owned is Result.Failed) return@run owned
            val own = (owned as Result.Success).value
            if (
                own != null &&
                    (own.request.accountId != actor.accountId ||
                        own.request.spaceId != spaceId ||
                        own.request.conversationId != conversationId ||
                        own.request.revision >= 999999 ||
                        own.state !in
                            setOf(
                                CollaborationSpaceProvisioningState.FAILED,
                                CollaborationSpaceProvisioningState.CANCELLED,
                            ))
            )
                return@run changed()
            if (status != null) {
                val earliest =
                    status.requestedAt.plusSeconds(COLLABORATION_PROVISIONING_RETRY_SECONDS)
                val now = clock.instant()
                if (now < earliest)
                    return@run Result.Failed(
                        Failure(
                            FailureKind.RATE_LIMITED,
                            "collaboration_provisioning_retry_too_soon",
                            parameters =
                                mapOf(
                                    "retryAfterSeconds" to
                                        ((Duration.between(now, earliest).toMillis() + 999) / 1000)
                                            .coerceAtLeast(1)
                                            .toString()
                                ),
                        )
                    )
            }
            val current = admission()
            if (current is Result.Failed) return@run current
            val observed = (current as Result.Success).value
            if (observed.provider != initial.provider) return@run observationChanged()
            val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val expiry =
                minOf(at.plusSeconds(300), observed.scope.expiresAt, observed.provider.expiresAt)
                    .truncatedTo(ChronoUnit.MICROS)
            if (expiry <= at || status?.lastOutcome?.at?.let { it > at } == true)
                return@run observationChanged()
            if (intent == null) {
                val original =
                    if (originalCompany == null) Result.Success(null)
                    else
                        conversations.legacyPair(
                            spaceId,
                            actor.accountId,
                            originalCompany,
                            conversation.firstAccountId,
                            conversation.secondAccountId,
                        )
                if (original is Result.Failed) return@run original
                val prepared =
                    prepareCollaborationSpaceRoomIntent(
                        initialPair,
                        actor.accountId,
                        (original as Result.Success).value,
                        at,
                    )
                if (prepared is Result.Failed) return@run prepared
                intent = (prepared as Result.Success).value
                val written = rooms.createIntent(intent)
                if (written is Result.Failed) return@run written
            }
            val request =
                CollaborationSpaceRoomRequest(
                    spaceId,
                    conversationId,
                    actor.accountId,
                    deviceId,
                    device.version,
                    observed.provider.generation,
                    parentId,
                    (own?.request?.revision ?: -1) + 1,
                    observed.scope.nativeSessionVersion,
                    observed.scope.credentialVersion,
                    actor.authenticatedAt,
                    actor.mfaVerifiedAt,
                    actor.correlationId,
                    currentClient,
                    at,
                    expiry,
                )
            val receipt = MutationReceipt(conversationId, request.revision)
            val written =
                if (own == null) rooms.enqueue(request)
                else rooms.retry(request, own.request.revision)
            written
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "collaboration_space_room",
                            conversationId,
                            if (own == null) "collaboration.space_room_requested"
                            else "collaboration.space_room_retried",
                            mapOf(
                                "spaceId" to spaceId.toString(),
                                "requestRevision" to request.revision.toString(),
                            ),
                        ),
                    )
                }
                .flatMap { finalAdmission().map { receipt } }
        }
    }

    private data class Observation(
        val scope: CollaborationSpaceAdmission,
        val provider: CollaborationSpaceProviderState,
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

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))

    private fun observationChanged() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_observation_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private fun notFound() =
        Result.Failed(Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found"))
}
