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
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

/** One currently admitted native device/room read. No provider I/O, cache or retry. */
class ReadNativeCollaborationDirectTransport(
    private val conversations: CollaborationDirectRepository,
    private val rooms: CollaborationDirectRoomRepository,
    private val registry: CollaborationRegistryRepository,
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
    ): Result<CollaborationDirectTransport> {
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
            // Room writers take their resource guard before policy/account guards too.
            val roomGuard = rooms.lock(company, conversationId)
            if (roomGuard is Result.Failed) return@run roomGuard
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
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
            val result = read(company, actor.accountId, sessionId, client, conversationId)
            // Expiry after acquisition also overrides missing/failed metadata results.
            admission().flatMap { result }
        }
    }

    private fun read(
        company: UUID,
        account: UUID,
        session: UUID,
        client: ClientVersion,
        conversationId: UUID,
    ): Result<CollaborationDirectTransport> {
        val found = conversations.find(company, conversationId)
        if (found is Result.Failed) return found
        val conversation = (found as Result.Success).value
        if (
            conversation == null ||
                conversation.id != conversationId ||
                conversation.companyId != company ||
                account !in setOf(conversation.firstAccountId, conversation.secondAccountId)
        )
            return Result.Failed(
                Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found")
            )
        val registered = registry.findBound(company, account, session)
        if (registered is Result.Failed) return registered
        val device = (registered as Result.Success).value
        if (
            device == null ||
                device.companyId != company ||
                device.accountId != account ||
                device.nativeSessionId != session ||
                device.state != CollaborationDeviceState.ACTIVE ||
                device.revokedAt != null ||
                device.version < 0 ||
                device.platform.name != client.platform.name
        )
            return Result.Failed(
                Failure(FailureKind.UNAVAILABLE, "collaboration_device_unavailable")
            )
        val provisioned = rooms.provisioning(company, conversationId)
        if (provisioned is Result.Failed) return provisioned
        val pointer = (provisioned as Result.Success).value
        val published = rooms.binding(company, conversationId)
        if (published is Result.Failed) return published
        val binding = (published as Result.Success).value
        if (pointer == null || binding == null)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_room_not_ready"))
        if (
            pointer.companyId != company ||
                pointer.conversationId != conversationId ||
                binding.companyId != company ||
                binding.conversationId != conversationId ||
                binding.accountId != pointer.accountId ||
                binding.jobId != pointer.jobId ||
                binding.provisioningVersion != pointer.version ||
                pointer.version !in 0..999999 ||
                binding.matrixServerName != provider.serverName ||
                binding.matrixRoomId.length !in 2..255 ||
                !binding.matrixRoomId.startsWith('!') ||
                binding.matrixRoomId.any { it.code !in 33..126 }
        )
            return Result.Failed(
                Failure(FailureKind.UNAVAILABLE, "collaboration_room_provisioning_changed")
            )
        return Result.Success(
            CollaborationDirectTransport(
                company,
                account,
                session,
                device.id,
                device.version,
                device.platform,
                conversationId,
                conversation.firstAccountId,
                conversation.secondAccountId,
                binding.matrixRoomId,
                binding.provisioningVersion,
            )
        )
    }
}
