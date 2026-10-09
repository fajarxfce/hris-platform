package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class PreviewAnnouncementAudience(
    private val announcements: AnnouncementRepository,
    private val groups: AudienceGroupRepository,
    private val audience: AnnouncementAudienceRepository,
    private val people: PeopleRepository,
    private val organization: OrganizationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
    ): Result<AnnouncementAudiencePreview> {
        val permission = actor.requirePermission("announcements.manage")
        if (permission is Result.Failed) return permission
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (expectedVersion < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        return transactions.run(actor) {
            val announcementGuard = announcements.lock(company, shared = true)
            if (announcementGuard is Result.Failed) return@run announcementGuard
            val groupGuard = groups.lock(company, shared = true)
            if (groupGuard is Result.Failed) return@run groupGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val structureGuard = organization.lockStructure(company, shared = true)
            if (structureGuard is Result.Failed) return@run structureGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanyCommandActor(actor, it) }
                    .flatMap { it.requirePermission("announcements.manage") }
            if (access is Result.Failed) return@run access
            val found = announcements.find(company, id)
            if (found is Result.Failed) return@run found
            val announcement =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "announcement_not_found")
                    )
            if (announcement.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (announcement.status !in setOf(AnnouncementStatus.DRAFT, AnnouncementStatus.QUEUED))
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "announcement_preview_unavailable")
                )
            val versions = linkedMapOf<UUID, Long>()
            when (announcement.audience.kind) {
                AudienceKind.COMPANY -> Unit
                AudienceKind.GROUP -> {
                    val loaded = groups.references(company, announcement.audience.targetIds.toSet())
                    if (loaded is Result.Failed) return@run loaded
                    val references = (loaded as Result.Success).value
                    val valid = validateAnnouncementGroups(announcement.audience, references)
                    if (valid is Result.Failed) return@run valid
                    references.forEach { versions[it.id] = it.version }
                }
                AudienceKind.BRANCH,
                AudienceKind.DEPARTMENT -> {
                    for (target in announcement.audience.targetIds) {
                        val loaded = organization.find(company, target)
                        if (loaded is Result.Failed) return@run loaded
                        val reference = (loaded as Result.Success).value
                        val valid = validateAnnouncementUnit(announcement.audience.kind, reference)
                        if (valid is Result.Failed) return@run valid
                        versions[target] = requireNotNull(reference).version
                    }
                }
            }
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "company_access_denied")
                    )
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val date = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            audience
                .recipients(
                    company,
                    announcementRecipientSelection(announcement.audience, date),
                    5001,
                )
                .flatMap { recipients ->
                    if (recipients.size > 5000)
                        Result.Failed(
                            Failure(
                                FailureKind.CONFLICT,
                                "announcement_audience_limit",
                                parameters = mapOf("maximum" to "5000"),
                            )
                        )
                    else
                        Result.Success(
                            AnnouncementAudiencePreview(
                                id,
                                announcement.version,
                                date,
                                now,
                                recipients.size,
                                versions.toMap(),
                            )
                        )
                }
        }
    }
}
