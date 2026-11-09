package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

/** One native device, published room and recipient observation. No provider I/O or setup retry. */
class ReadNativeCollaborationSpaceDirectTransport(
    private val rooms: CollaborationSpaceRoomRepository,
    private val conversations: CollaborationSpaceConversationRepository,
    private val registry: CollaborationSpaceDeviceRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val gateway: CollaborationSpaceGatewaySessionRepository,
    private val recipients: CollaborationSpaceRecipientRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        parentId: UUID,
        parentVersion: Long,
        spaceId: UUID,
        conversationId: UUID,
        client: ClientVersion?,
    ): Result<CollaborationSpaceDirectTransport> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (listOf(parentId, spaceId, conversationId).any { it == UUID(0, 0) } || parentVersion < 0)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_conversation_query")
            )
        val started = clock.instant()
        return transactions.run(actor) {
            val roomGuard = rooms.lock(spaceId, conversationId)
            if (roomGuard is Result.Failed) return@run roomGuard
            // Only exact immutable coordinates may select account guards. Native admission still
            // precedes disclosure of a missing, failed or malformed conversation observation.
            val found = conversations.find(spaceId, actor.accountId, conversationId)
            val conversation = (found as? Result.Success)?.value
            val ownsPair =
                conversation != null &&
                    conversation.spaceId == spaceId &&
                    conversation.id == conversationId &&
                    conversation.firstAccountId != UUID(0, 0) &&
                    conversation.secondAccountId != UUID(0, 0) &&
                    conversation.firstAccountId.toString() <
                        conversation.secondAccountId.toString() &&
                    conversation.version == 0L &&
                    conversation.createdAt <= started &&
                    conversation.createdAt.nano % 1000 == 0 &&
                    conversation.createdAt.epochSecond in -62135596800L..253402300799L &&
                    conversation.createdBy in
                        setOf(conversation.firstAccountId, conversation.secondAccountId) &&
                    actor.accountId in
                        setOf(conversation.firstAccountId, conversation.secondAccountId)
            val accounts =
                if (ownsPair)
                    listOf(
                        requireNotNull(conversation).firstAccountId,
                        conversation.secondAccountId,
                    )
                else listOf(actor.accountId)
            for (account in accounts.sorted()) {
                val guarded = identities.lockAccount(account, shared = true)
                if (guarded is Result.Failed) return@run guarded
            }
            val locked = sessions.lock(parentId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun nativeAdmission(): Result<CollaborationSpaceAdmission> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val selected = spaces.current()
                if (selected is Result.Failed) return selected
                val at = clock.instant()
                if (at < started || at >= started.plusSeconds(30))
                    return Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed")
                    )
                return admitCollaborationSpaceSession(
                        actor,
                        (acquired as Result.Success).value,
                        parent,
                        parentId,
                        parentVersion,
                        (selected as Result.Success).value,
                        client,
                        policy,
                        security,
                        at,
                    )
                    .flatMap { if (it.spaceId == spaceId) Result.Success(it) else denied() }
            }
            fun admission(): Result<CollaborationSpaceAdmission> {
                val before = nativeAdmission()
                if (before is Result.Failed) return before
                val peers =
                    if (ownsPair) {
                        var failure: Result.Failed? = null
                        for (account in accounts) {
                            val member = conversations.member(spaceId, account)
                            if (member is Result.Failed) {
                                failure = member
                                break
                            }
                            val value = (member as Result.Success).value
                            if (
                                value == null ||
                                    value.spaceId != spaceId ||
                                    value.accountId != account
                            ) {
                                failure =
                                    Result.Failed(
                                        Failure(
                                            FailureKind.FORBIDDEN,
                                            "collaboration_peer_unavailable",
                                        )
                                    )
                                break
                            }
                        }
                        failure
                    } else null
                val current = nativeAdmission()
                return if (current is Result.Failed) current else peers ?: current
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val result =
                if (found is Result.Failed) found
                else if (!ownsPair)
                    Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found")
                    )
                else read((admitted as Result.Success).value, requireNotNull(conversation))
            admission().flatMap { result }
        }
    }

    private fun read(
        admission: CollaborationSpaceAdmission,
        conversation: CollaborationSpaceDirectConversation,
    ): Result<CollaborationSpaceDirectTransport> {
        val space = admission.spaceId
        val account = admission.accountId
        val members = mutableListOf<CollaborationSpaceMember>()
        for (id in listOf(conversation.firstAccountId, conversation.secondAccountId)) {
            val member = conversations.member(space, id)
            if (member is Result.Failed) return member
            val value = (member as Result.Success).value
            if (value == null || value.spaceId != space || value.accountId != id)
                return Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "collaboration_peer_unavailable")
                )
            members += value
        }
        val deviceRead = registry.findBound(space, account, admission.nativeSessionId)
        if (deviceRead is Result.Failed) return deviceRead
        val device = (deviceRead as Result.Success).value
        if (
            device == null ||
                device.spaceId != space ||
                device.accountId != account ||
                device.originNativeSessionId != admission.nativeSessionId ||
                device.platform.name != admission.client.platform.name ||
                device.state != CollaborationDeviceState.ACTIVE ||
                device.revokedAt != null ||
                device.version !in 1..999999
        )
            return unavailableDevice()
        val own = members.single { it.accountId == account }.identity
        val peer = members.single { it.accountId != account }
        val peerIdentity = peer.identity
        if (
            own == null ||
                peerIdentity == null ||
                own.serverName != provider.serverName ||
                peerIdentity.serverName != provider.serverName ||
                own.userId != device.matrixUserId ||
                own.selectedAt > clock.instant() ||
                peerIdentity.selectedAt > clock.instant()
        )
            return changedRoom()
        val intent = rooms.intent(space, account, conversation.id)
        if (intent is Result.Failed) return intent
        val status = rooms.status(space, account, conversation.id)
        if (status is Result.Failed) return status
        val binding = rooms.binding(space, account, conversation.id)
        if (binding is Result.Failed) return binding
        val roomRead =
            collaborationSpaceRoomView(
                space,
                conversation.id,
                conversation,
                (intent as Result.Success).value,
                (status as Result.Success).value,
                (binding as Result.Success).value,
                provider.serverName,
                clock.instant(),
            )
        if (roomRead is Result.Failed) return roomRead
        val room =
            (roomRead as Result.Success).value.room
                ?: return Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_room_not_ready")
                )
        val immutable = requireNotNull(intent.value)
        if (
            immutable.firstMatrixUserId != members[0].identity?.userId ||
                immutable.secondMatrixUserId != members[1].identity?.userId
        )
            return changedRoom()
        val work = provisioning.metadata(space, account, device.id)
        if (work is Result.Failed) return work
        val head = (work as Result.Success).value ?: return unavailableDevice()
        val observedGateway = gateway.find(space, account, device.id)
        if (observedGateway is Result.Failed) return observedGateway
        val current = (observedGateway as Result.Success).value
        if (current == null || current.revokedAt != null || current.expiresAt <= clock.instant())
            return unavailableDevice()
        val deviceStatus = collaborationSpaceDeviceStatus(device, head, clock.instant(), current)
        if (deviceStatus is Result.Failed) return deviceStatus
        val candidates = recipients.devices(space, setOf(peer.accountId), clock.instant())
        if (candidates is Result.Failed) return candidates
        val ready =
            collaborationSpaceRecipientReady(
                space,
                peer.accountId,
                provider.serverName,
                peerIdentity.userId,
                clock.instant(),
                (candidates as Result.Success).value,
            )
        if (ready is Result.Failed) return ready
        if (current.expiresAt <= clock.instant()) return unavailableDevice()
        return Result.Success(
            CollaborationSpaceDirectTransport(
                space,
                account,
                admission.nativeSessionId,
                admission.nativeSessionVersion,
                conversation,
                (deviceStatus as Result.Success).value,
                device.matrixUserId,
                device.matrixDeviceId,
                room,
                (ready as Result.Success).value,
            )
        )
    }

    private fun unavailableDevice() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_device_unavailable"))

    private fun changedRoom() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
}
