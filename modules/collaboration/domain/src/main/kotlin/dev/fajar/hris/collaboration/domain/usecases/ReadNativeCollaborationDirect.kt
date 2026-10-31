package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDirectRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

/** Retained DM metadata is available to a currently admitted participant even if the peer left. */
class ReadNativeCollaborationDirect(
    private val conversations: CollaborationDirectRepository,
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
    fun list(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationDirectConversation>> {
        if (limit !in 1..50)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_conversation_query")
            )
        return read(actor, sessionId, sessionVersion, client) {
            conversations.list(requireNotNull(actor.companyId), actor.accountId, after, limit)
        }
    }

    fun get(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        id: UUID,
    ): Result<CollaborationDirectConversation> =
        read(actor, sessionId, sessionVersion, client) {
            conversations.find(requireNotNull(actor.companyId), id).flatMap { conversation ->
                if (conversation == null)
                    Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found")
                    )
                else Result.Success(conversation)
            }
        }

    private fun <T> read(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
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
