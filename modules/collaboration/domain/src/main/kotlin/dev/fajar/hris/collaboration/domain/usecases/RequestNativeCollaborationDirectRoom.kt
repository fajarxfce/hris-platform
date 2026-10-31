package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Initial setup or explicit terminal retry, with one retained creator and no provider I/O. */
class RequestNativeCollaborationDirectRoom(
    private val conversations: CollaborationDirectRepository,
    private val rooms: CollaborationDirectRoomRepository,
    private val registry: CollaborationRegistryRepository,
    private val jobs: JobRepository,
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        operationId: UUID,
        conversationId: UUID,
        deviceId: UUID,
        expectedDeviceVersion: Long,
        expectedProvisioningVersion: Long?,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission(COLLABORATION_CHAT_PERMISSION)
        if (permission is Result.Failed) return permission
        if (!service.enabled || !provider.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (
            client == null ||
                client.platform !in setOf(ClientPlatform.ANDROID, ClientPlatform.IOS) ||
                client.build <= 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_client_version"))
        if (
            expectedDeviceVersion !in 0..999999 ||
                (expectedProvisioningVersion != null && expectedProvisioningVersion !in 0..999998)
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_provisioning_version")
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "collaboration.direct_room_setup",
                operationId,
                listOf(
                    sessionId.toString(),
                    conversationId.toString(),
                    deviceId.toString(),
                    expectedDeviceVersion.toString(),
                    expectedProvisioningVersion?.toString() ?: "initial",
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val roomGuard = rooms.lock(company, conversationId)
            if (roomGuard is Result.Failed) return@run roomGuard
            val found = conversations.find(company, conversationId)
            if (found is Result.Failed) return@run found
            val conversation =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found")
                    )
            if (
                conversation.companyId != company ||
                    conversation.id != conversationId ||
                    actor.accountId !in
                        listOf(conversation.firstAccountId, conversation.secondAccountId)
            )
                return@run changed()
            val pg = policies.lock(company, shared = true)
            if (pg is Result.Failed) return@run pg
            val cg = companies.lock(company, shared = true)
            if (cg is Result.Failed) return@run cg
            val mg = members.lock(company, shared = true)
            if (mg is Result.Failed) return@run mg
            for (account in
                listOf(conversation.firstAccountId, conversation.secondAccountId).sorted()) {
                val ag = identities.lockAccount(account, shared = true)
                if (ag is Result.Failed) return@run ag
            }
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            if (parent?.id != sessionId) return@run nativeAuthenticationRequired()
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            fun admission(): Result<Unit> =
                policies.effective(company, clock.instant()).flatMap {
                    val at = clock.instant()
                    nativeCollaborationClientDeadline(it, client, at).flatMap {
                        collaborationParentDeadline(
                                actor,
                                access,
                                parent,
                                sessionVersion,
                                at,
                                security,
                            )
                            .map { Unit }
                    }
                }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val peerId =
                if (actor.accountId == conversation.firstAccountId) conversation.secondAccountId
                else conversation.firstAccountId
            val selected = identities.access(peerId, company)
            if (selected is Result.Failed) return@run selected
            val peer = (selected as Result.Success).value
            if (
                peer == null ||
                    peer.account.id != peerId ||
                    !peer.account.active ||
                    !peer.companyActive ||
                    !peer.membershipActive ||
                    COLLABORATION_CHAT_PERMISSION !in peer.permissions
            )
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "collaboration_peer_unavailable")
                )
            val deviceRead = registry.findBound(company, actor.accountId, sessionId)
            if (deviceRead is Result.Failed) return@run deviceRead
            val device = (deviceRead as Result.Success).value
            if (
                device == null ||
                    device.id != deviceId ||
                    device.companyId != company ||
                    device.accountId != actor.accountId ||
                    device.nativeSessionId != sessionId ||
                    device.platform.name != client.platform.name ||
                    device.state != CollaborationDeviceState.ACTIVE ||
                    device.revokedAt != null ||
                    device.createdAt > clock.instant()
            )
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
                )
            val read = rooms.provisioning(company, conversationId)
            if (read is Result.Failed) return@run read
            val pointer = (read as Result.Success).value
            val oldReceipt = (replay as Result.Success).value
            if (oldReceipt != null) {
                if (
                    oldReceipt.id != conversationId ||
                        pointer == null ||
                        pointer.version < oldReceipt.version
                )
                    return@run changed()
                return@run admission().map { oldReceipt }
            }
            if (device.version != expectedDeviceVersion) return@run changed()
            val bound = rooms.binding(company, conversationId)
            if (bound is Result.Failed) return@run bound
            if (
                (bound as Result.Success).value != null ||
                    (expectedProvisioningVersion == null && pointer != null)
            ) {
                if (pointer == null) return@run changed()
                val receipt = MutationReceipt(conversationId, pointer.version)
                return@run operations.record(actor, key, receipt).flatMap {
                    admission().map { receipt }
                }
            }
            if (expectedProvisioningVersion != null) {
                if (
                    pointer == null ||
                        pointer.version != expectedProvisioningVersion ||
                        pointer.updatedAt > clock.instant()
                )
                    return@run changed()
                if (pointer.accountId != actor.accountId)
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "collaboration_room_creator_required")
                    )
                val previous = jobs.find(company, pointer.jobId)
                if (previous is Result.Failed) return@run previous
                val job = (previous as Result.Success).value ?: return@run changed()
                if (
                    job.request.kind != JobKind.COLLABORATION_DIRECT_PROVISION ||
                        job.request.actorId != actor.accountId ||
                        job.request.companyId != company ||
                        job.request.values["conversationId"] != conversationId.toString() ||
                        job.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
                )
                    return@run changed()
                val earliest = pointer.updatedAt.plusSeconds(60)
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
                                            .toString()
                                ),
                        )
                    )
            } else if (pointer != null) return@run changed()
            val queue = jobs.lockQueue(company)
            if (queue is Result.Failed) return@run queue
            val count = jobs.pendingCount(company)
            if (count is Result.Failed) return@run count
            if ((count as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.COLLABORATION_DIRECT_PROVISION,
                    operationId,
                    mapOf(
                        "conversationId" to conversationId.toString(),
                        "deviceId" to deviceId.toString(),
                        "nativeSessionId" to sessionId.toString(),
                        "platform" to device.platform.name,
                        "clientBuild" to client.build.toString(),
                    ),
                    parent.authenticatedAt,
                    parent.credentialVersion,
                    actor.correlationId,
                    at,
                    1,
                )
            val next =
                CollaborationDirectRoomProvisioning(
                    company,
                    conversationId,
                    actor.accountId,
                    deviceId,
                    job.id,
                    (pointer?.version ?: -1) + 1,
                    pointer?.createdAt ?: at,
                    at,
                )
            val receipt = MutationReceipt(conversationId, next.version)
            jobs
                .create(job)
                .flatMap {
                    if (pointer == null) rooms.create(next)
                    else rooms.replace(next, pointer.version)
                }
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "collaboration_direct_conversation",
                            conversationId,
                            "collaboration.direct_room_requested",
                            mapOf("version" to next.version.toString()),
                        ),
                    )
                }
                .flatMap { admission().map { receipt } }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))
}
