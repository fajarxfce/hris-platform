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
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Owner-scoped metadata only. Matrix message content retains its independent transport. */
class SyncNativeCollaborationSummaries(
    private val summaries: CollaborationSummaryReadRepository,
    private val cursors: CollaborationSummaryCursorRepository,
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
    fun bootstrap(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        token: String?,
        limit: Int,
    ): Result<CollaborationSummaryBootstrap> =
        read(
            actor,
            sessionId,
            sessionVersion,
            client,
            token,
            limit,
            CollaborationSummaryPhase.BOOTSTRAP,
        ) { cursor, _ ->
            val acquired = summaries.snapshot(cursor.scope.owner, cursor.after, limit + 1)
            if (acquired is Result.Failed) return@read acquired
            val rows = (acquired as Result.Success).value
            if (
                rows.size > limit + 1 ||
                    rows.any { it.revision < 0 } ||
                    rows.zipWithNext().any { (a, b) ->
                        a.conversationId.toString() >= b.conversationId.toString()
                    } ||
                    cursor.after?.let { after ->
                        rows.firstOrNull()?.conversationId?.toString()?.let {
                            it <= after.toString()
                        }
                    } == true
            )
                return@read invalidPage()
            val items = rows.take(limit)
            val more = rows.size > limit
            if (more && cursor.page >= 999)
                return@read Result.Failed(
                    Failure(FailureKind.CONFLICT, "collaboration_summary_bootstrap_limit")
                )
            val pending = summaries.pending(cursor.scope.owner)
            if (pending is Result.Failed) return@read pending
            val now = clock.instant().truncatedTo(ChronoUnit.SECONDS)
            val next =
                if (more) cursor.copy(after = items.last().conversationId, page = cursor.page + 1)
                else
                    cursor.copy(
                        phase = CollaborationSummaryPhase.CHANGES,
                        after = null,
                        page = 0,
                        issuedAt = now,
                        expiresAt = now.plusSeconds(604800),
                    )
            cursors.seal(next).map { encoded ->
                CollaborationSummaryBootstrap(
                    items,
                    encoded.takeIf { more },
                    encoded.takeIf { !more },
                    (pending as Result.Success).value,
                    clock.instant(),
                )
            }
        }

    fun changes(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        token: String,
        limit: Int,
    ): Result<CollaborationSummaryChanges> =
        read(
            actor,
            sessionId,
            sessionVersion,
            client,
            token,
            limit,
            CollaborationSummaryPhase.CHANGES,
        ) { cursor, head ->
            val upper = cursor.upperPosition ?: head.position
            val acquired = summaries.changes(cursor.scope.owner, cursor.position, upper, limit + 1)
            if (acquired is Result.Failed) return@read acquired
            val rows = (acquired as Result.Success).value
            if (
                rows.size > limit + 1 ||
                    rows.any {
                        it.position <= cursor.position || it.position > upper || it.revision < 0
                    } ||
                    rows.zipWithNext().any { (a, b) -> a.position >= b.position }
            )
                return@read invalidPage()
            val selected = rows.take(limit)
            val more = rows.size > limit
            val ids = selected.map { it.conversationId }.distinct()
            val loaded =
                if (ids.isEmpty()) Result.Success(emptyList())
                else summaries.facts(cursor.scope.owner, ids)
            if (loaded is Result.Failed) return@read loaded
            val facts = (loaded as Result.Success).value
            if (facts.size != ids.size || facts.map { it.conversationId }.toSet() != ids.toSet())
                return@read invalidPage()
            val byId = facts.associateBy { it.conversationId }
            if (selected.any { requireNotNull(byId[it.conversationId]).revision < it.revision })
                return@read invalidPage()
            val items =
                selected.map {
                    CollaborationSummaryChange(it.position, requireNotNull(byId[it.conversationId]))
                }
            val pending = summaries.pending(cursor.scope.owner)
            if (pending is Result.Failed) return@read pending
            val now = clock.instant().truncatedTo(ChronoUnit.SECONDS)
            val next =
                if (more) cursor.copy(position = selected.last().position, upperPosition = upper)
                else
                    cursor.copy(
                        position = upper,
                        upperPosition = null,
                        issuedAt = now,
                        expiresAt = now.plusSeconds(604800),
                    )
            cursors.seal(next).map {
                CollaborationSummaryChanges(
                    items,
                    it,
                    more,
                    (pending as Result.Success).value,
                    clock.instant(),
                )
            }
        }

    private fun <T> read(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        token: String?,
        limit: Int,
        phase: CollaborationSummaryPhase,
        acquire: (CollaborationSummaryCursor, CollaborationSummaryHead) -> Result<T>,
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
        if (limit !in 1..50)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_conversation_query")
            )
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
            fun load(): Result<T> {
                val companyRead = companies.find(company)
                if (companyRead is Result.Failed) return companyRead
                val companyValue = (companyRead as Result.Success).value ?: return denied()
                val memberRead = members.find(company, actor.accountId)
                if (memberRead is Result.Failed) return memberRead
                val member = (memberRead as Result.Success).value ?: return denied()
                val scope =
                    CollaborationSummaryScope(
                        CollaborationSummaryOwner(company, actor.accountId, sessionId),
                        requireNotNull(access).account.securityVersion,
                        companyValue.version,
                        member.version,
                    )
                val storedHead = summaries.head(company)
                if (storedHead is Result.Failed) return storedHead
                val head = (storedHead as Result.Success).value
                val now = clock.instant().truncatedTo(ChronoUnit.SECONDS)
                val decoded =
                    if (token == null && phase == CollaborationSummaryPhase.BOOTSTRAP)
                        Result.Success(
                            CollaborationSummaryCursor(
                                scope,
                                head.epoch,
                                phase,
                                head.position,
                                now,
                                now.plusSeconds(900),
                            )
                        )
                    else cursors.open(scope.owner, token ?: "")
                if (decoded is Result.Failed) return decoded
                val cursor = (decoded as Result.Success).value
                val valid =
                    validateCollaborationSummaryCursor(cursor, scope, head, phase, clock.instant())
                if (valid is Result.Failed) return valid
                val result = acquire(cursor, head)
                return validateCollaborationSummaryCursorTime(cursor, clock.instant()).flatMap {
                    result
                }
            }
            val result = load()
            admission().flatMap { result }
        }
    }

    private fun invalidPage() =
        Result.Failed(Failure(FailureKind.UNEXPECTED, "collaboration_summary_page_invalid"))

    private fun denied() = Result.Failed(Failure(FailureKind.FORBIDDEN, "company_access_denied"))
}
