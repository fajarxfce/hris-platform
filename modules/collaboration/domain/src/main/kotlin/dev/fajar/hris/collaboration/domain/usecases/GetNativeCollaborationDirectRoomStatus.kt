package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDirectRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDirectRoomRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobKind
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

/** Safe setup status; room metadata is not content authorization. */
class GetNativeCollaborationDirectRoomStatus(
    private val conversations: CollaborationDirectRepository,
    private val rooms: CollaborationDirectRoomRepository,
    private val jobs: JobRepository,
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
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
        conversationId: UUID,
    ): Result<CollaborationDirectRoomStatus> =
        read(actor, sessionId, sessionVersion, client, conversationId) {
            val company = requireNotNull(actor.companyId)
            val conversation = conversations.find(company, conversationId)
            if (conversation is Result.Failed) return@read conversation
            val value = (conversation as Result.Success).value
            if (
                value == null ||
                    value.id != conversationId ||
                    value.companyId != company ||
                    actor.accountId !in listOf(value.firstAccountId, value.secondAccountId)
            )
                return@read Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found")
                )
            val provisioned = rooms.provisioning(company, conversationId)
            if (provisioned is Result.Failed) return@read provisioned
            val pointer =
                (provisioned as Result.Success).value
                    ?: return@read Result.Success(
                        CollaborationDirectRoomStatus(
                            conversationId,
                            null,
                            CollaborationDirectRoomState.NOT_REQUESTED,
                            null,
                            null,
                            false,
                            null,
                        )
                    )
            val binding = rooms.binding(company, conversationId)
            if (binding is Result.Failed) return@read binding
            val bound = (binding as Result.Success).value
            if (bound != null) {
                if (
                    bound.companyId != company ||
                        bound.conversationId != conversationId ||
                        bound.accountId != pointer.accountId ||
                        bound.jobId != pointer.jobId ||
                        bound.provisioningVersion != pointer.version ||
                        bound.matrixServerName != provider.serverName
                )
                    return@read changed()
                return@read Result.Success(
                    CollaborationDirectRoomStatus(
                        conversationId,
                        pointer.version,
                        CollaborationDirectRoomState.READY,
                        bound.matrixRoomId,
                        pointer.accountId,
                        false,
                        null,
                    )
                )
            }
            val acquired = jobs.find(company, pointer.jobId)
            if (acquired is Result.Failed) return@read acquired
            val job = (acquired as Result.Success).value ?: return@read changed()
            if (
                job.request.companyId != company ||
                    job.request.actorId != pointer.accountId ||
                    job.request.kind != JobKind.COLLABORATION_DIRECT_PROVISION ||
                    job.request.values["conversationId"] != conversationId.toString()
            )
                return@read changed()
            val state =
                when (job.status) {
                    JobStatus.QUEUED -> CollaborationDirectRoomState.QUEUED
                    JobStatus.RUNNING -> CollaborationDirectRoomState.RUNNING
                    JobStatus.FAILED -> CollaborationDirectRoomState.FAILED
                    JobStatus.CANCELLED -> CollaborationDirectRoomState.CANCELLED
                    JobStatus.SUCCEEDED -> return@read changed()
                }
            val retryable =
                pointer.accountId == actor.accountId &&
                    pointer.version < 999999 &&
                    job.status in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
            Result.Success(
                CollaborationDirectRoomStatus(
                    conversationId,
                    pointer.version,
                    state,
                    null,
                    pointer.accountId,
                    retryable,
                    if (retryable) pointer.updatedAt.plusSeconds(60) else null,
                )
            )
        }

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_room_provisioning_changed"))

    private fun <T> read(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        conversationId: UUID,
        acquire: () -> Result<T>,
    ): Result<T> {
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
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val guard = rooms.lock(company, conversationId)
            if (guard is Result.Failed) return@run guard
            val pg = policies.lock(company, shared = true)
            if (pg is Result.Failed) return@run pg
            val cg = companies.lock(company, shared = true)
            if (cg is Result.Failed) return@run cg
            val mg = members.lock(company, shared = true)
            if (mg is Result.Failed) return@run mg
            val ag = identities.lockAccount(actor.accountId, shared = true)
            if (ag is Result.Failed) return@run ag
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
            val result = acquire()
            // Even a not-found/error outcome cannot substitute for current session admission.
            admission().flatMap { result }
        }
    }
}
