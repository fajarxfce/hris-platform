package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

class UpdateInboxItem(
    private val inbox: InboxRepository,
    private val announcements: AnnouncementRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        action: InboxAction,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission("announcements.read")
        if (permission is Result.Failed) return permission
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (expectedVersion < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "communications.inbox_update",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), action.name),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = announcements.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
            val loaded = inbox.find(company, actor.accountId, id, lock = true)
            if (loaded is Result.Failed) return@run loaded
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanyCommandActor(actor, it) }
                    .flatMap { it.requirePermission("announcements.read") }
            if (checked is Result.Failed) return@run checked
            val item =
                (loaded as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "inbox_item_not_found")
                    )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (item.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (action == InboxAction.ACKNOWLEDGE && !item.acknowledgementRequired)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "inbox_acknowledgement_not_required")
                )
            if (
                (action == InboxAction.READ && item.readAt != null) ||
                    (action == InboxAction.ACKNOWLEDGE && item.acknowledgedAt != null)
            ) {
                val receipt = MutationReceipt(id, item.version)
                return@run operations.record(actor, key, receipt).map { receipt }
            }
            val now =
                maxOf(
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                    item.readAt ?: item.deliveredAt,
                )
            val readAt = item.readAt ?: now
            val acknowledgedAt = if (action == InboxAction.ACKNOWLEDGE) now else item.acknowledgedAt
            inbox
                .saveReadState(
                    company,
                    actor.accountId,
                    id,
                    expectedVersion,
                    readAt,
                    acknowledgedAt,
                )
                .requireCurrentVersion()
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "inbox_item",
                                    id,
                                    if (action == InboxAction.READ) "communications.inbox_read"
                                    else "communications.inbox_acknowledged",
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
