package dev.fajar.hris

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class CommunicationsReadCompletionTest : AnnouncementPublicationApiFixture() {
    @Autowired private lateinit var announcements: AnnouncementRepository
    @Autowired private lateinit var groups: AudienceGroupRepository
    @Autowired private lateinit var recipients: AnnouncementAudienceRepository
    @Autowired private lateinit var inboxRepository: InboxRepository
    @Autowired private lateinit var references: AudienceReferenceRepository
    @Autowired private lateinit var organization: OrganizationRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "announcements",
                "message",
                "message_revision",
                "history",
                "groups",
                "group",
                "group_revision",
                "preview",
                "inbox",
                "unread",
                "item",
                "references",
                "selected_references",
            ]
    )
    fun acquiredContentAndCountsRequireAssuranceAtReadCompletion(mode: String) {
        val f = fixture()
        val permissions = setOf("announcements.manage", "announcements.read")
        val recipient = member(f, permissions.toList())
        val id = draft(f)
        val group = group(f, members = listOf(recipient.employment))
        var item = UUID.randomUUID()
        if (mode in setOf("inbox", "unread", "item")) {
            published(f, id)
            item = UUID.fromString(inbox(f, recipient).single()["id"].asString())
        }
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                recipient.account,
            )
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            f.actor.copy(
                accountId = recipient.account,
                permissions = permissions,
                credentialVersion = 0,
                mfaVerifiedAt = original,
            )
        var acquisitions = 0
        fun <T> expire(result: Result<T>): Result<T> {
            assertTrue(result is Result.Success, "Actual fixture acquisition must succeed")
            acquisitions++
            clock.set(original.plusSeconds(2))
            return result
        }
        val messages =
            object : AnnouncementRepository by announcements {
                override fun find(
                    companyId: UUID,
                    id: UUID,
                    revision: Long?,
                ): Result<Announcement?> {
                    val result = announcements.find(companyId, id, revision)
                    return if (mode in setOf("message", "message_revision")) expire(result)
                    else result
                }

                override fun list(companyId: UUID, after: UUID?, limit: Int) =
                    expire(announcements.list(companyId, after, limit))

                override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                    expire(announcements.history(companyId, id, after, limit))
            }
        val groupSource =
            object : AudienceGroupRepository by groups {
                override fun find(companyId: UUID, id: UUID, revision: Long?) =
                    expire(groups.find(companyId, id, revision))

                override fun list(companyId: UUID, after: UUID?, limit: Int) =
                    expire(groups.list(companyId, after, limit))
            }
        val inboxSource =
            object : InboxRepository by inboxRepository {
                override fun countUnread(companyId: UUID, accountId: UUID) =
                    expire(inboxRepository.countUnread(companyId, accountId))

                override fun list(companyId: UUID, accountId: UUID, after: UUID?, limit: Int) =
                    expire(inboxRepository.list(companyId, accountId, after, limit))

                override fun find(companyId: UUID, accountId: UUID, id: UUID, lock: Boolean) =
                    expire(inboxRepository.find(companyId, accountId, id, lock))
            }
        val recipientSource =
            object : AnnouncementAudienceRepository by recipients {
                override fun recipients(
                    companyId: UUID,
                    selection: AnnouncementRecipientSelection,
                    limit: Int,
                ) = expire(recipients.recipients(companyId, selection, limit))
            }
        val referenceSource =
            object : AudienceReferenceRepository by references {
                override fun list(
                    companyId: UUID,
                    search: AudienceReferenceSearch,
                    activeOnly: Boolean,
                ) = expire(references.list(companyId, search, activeOnly))
            }
        fun read(current: Actor): Result<*> =
            when (mode) {
                "announcements" ->
                    ListAnnouncements(
                            messages,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current)
                "message",
                "message_revision" ->
                    GetAnnouncement(
                            messages,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, id, if (mode == "message_revision") 0 else null)
                "history" ->
                    ListAnnouncementHistory(
                            messages,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, id)
                "groups" ->
                    ListAudienceGroups(
                            groupSource,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current)
                "group",
                "group_revision" ->
                    GetAudienceGroup(
                            groupSource,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, group, if (mode == "group_revision") 0 else null)
                "preview" ->
                    PreviewAnnouncementAudience(
                            messages,
                            groups,
                            recipientSource,
                            people,
                            organization,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, id, 0)
                "inbox" ->
                    ListInbox(
                            inboxSource,
                            announcements,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current)
                "unread" ->
                    CountUnreadInbox(
                            inboxSource,
                            announcements,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current)
                "item" ->
                    GetInboxItem(
                            inboxSource,
                            announcements,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, item)
                else ->
                    ListAudienceReferences(
                            referenceSource,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(
                            current,
                            AudienceReferenceSearch(
                                AudienceReferenceKind.EMPLOYMENT,
                                ids =
                                    if (mode == "selected_references") listOf(recipient.employment)
                                    else emptyList(),
                            ),
                        )
            }
        val tables =
            listOf(
                "announcement_revisions",
                "audience_group_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
        val before = tables.associateWith { count(f, it) }
        assertTrue(read(actor) is Result.Success)
        assertEquals(1, acquisitions)
        clock.set(original)
        val expired =
            read(actor.copy(mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1)))
        assertEquals(2, acquisitions)
        assertTrue(
            expired is Result.Failed,
            "Expired MFA must not return acquired correspondence or metadata",
        )
        assertEquals("mfa_required", (expired as Result.Failed).failure.code)
        assertTrue(read(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
        assertEquals(3, acquisitions)
        for (table in tables) assertEquals(before[table], count(f, table), table)
    }
}
