package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import java.time.Clock
import java.util.UUID

class SaveAnnouncement(
    private val announcements: AnnouncementRepository,
    private val groups: AudienceGroupRepository,
    private val organization: OrganizationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        request: SaveAnnouncementCommand,
    ): Result<MutationReceipt> {
        val allowed = actor.requirePermission("announcements.manage")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val input =
            request.copy(
                title = request.title.trim(),
                body = request.body.trim(),
                audience = request.audience.copy(targetIds = request.audience.targetIds.toList()),
                reason = request.reason.trim(),
            )
        val valid = validateAnnouncementInput(input)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "communications.announcement_save",
                operationId,
                listOf(
                    input.id.toString(),
                    input.expectedVersion?.toString(),
                    input.title,
                    input.body,
                    input.audience.kind.name,
                    input.acknowledgementRequired.toString(),
                    input.reason,
                ) + input.audience.targetIds.map { it.toString() }.sorted(),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val announcementGuard = announcements.lock(company)
            if (announcementGuard is Result.Failed) return@run announcementGuard
            val groupGuard = groups.lock(company, shared = true)
            if (groupGuard is Result.Failed) return@run groupGuard
            val structureGuard = organization.lockStructure(company, shared = true)
            if (structureGuard is Result.Failed) return@run structureGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("announcements.manage") }
            if (authorized is Result.Failed) return@run authorized
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = announcements.find(company, input.id)
            if (found is Result.Failed) return@run found
            val previous = (found as Result.Success).value
            if (previous?.version != input.expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (previous != null && previous.status != AnnouncementStatus.DRAFT)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "announcement_not_draft"))
            if (previous != null && previous.version >= 996)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "announcement_revision_limit")
                )
            if (previous == null) {
                val count = announcements.count(company)
                if (count is Result.Failed) return@run count
                if ((count as Result.Success).value >= 10000)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "announcement_limit"))
            }
            when (input.audience.kind) {
                AudienceKind.COMPANY -> Unit
                AudienceKind.GROUP -> {
                    val references =
                        groups.references(company, input.audience.targetIds.toSet()).flatMap {
                            validateAnnouncementGroups(input.audience, it)
                        }
                    if (references is Result.Failed) return@run references
                }
                AudienceKind.BRANCH,
                AudienceKind.DEPARTMENT -> {
                    for (target in input.audience.targetIds) {
                        val reference =
                            organization.find(company, target).flatMap {
                                validateAnnouncementUnit(input.audience.kind, it)
                            }
                        if (reference is Result.Failed) return@run reference
                    }
                }
            }
            val snapshot =
                Announcement(
                    input.id,
                    (previous?.version ?: -1L) + 1,
                    input.title,
                    input.body,
                    input.audience,
                    input.acknowledgementRequired,
                    AnnouncementStatus.DRAFT,
                    clock.instant(),
                    actor.accountId,
                    input.reason,
                    publicationAttempts = previous?.publicationAttempts ?: 0,
                )
            announcements.save(company, snapshot, input.expectedVersion).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "announcement",
                                input.id,
                                "communications.announcement_saved",
                                mapOf("version" to receipt.version.toString()),
                                input.reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
