package dev.fajar.hris

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.BackgroundJob
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class CommunicationsAssuranceTest : AnnouncementPublicationApiFixture() {
    @Autowired private lateinit var announcements: AnnouncementRepository
    @Autowired private lateinit var groups: AudienceGroupRepository
    @Autowired private lateinit var recipients: AnnouncementAudienceRepository
    @Autowired private lateinit var inboxRepository: InboxRepository
    @Autowired private lateinit var organization: OrganizationRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy()

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "announcements",
                "message",
                "message_revision",
                "publication_review",
                "history",
                "groups",
                "group",
                "group_revision",
                "preview",
                "save",
                "save_group",
                "publish",
                "archive",
                "return",
                "inbox",
                "item",
                "read",
                "ack",
            ]
    )
    fun interactiveReadsAndOriginalReceiptsRequireCurrentAssuranceAfterWaiting(mode: String) {
        val f = fixture()
        val id = draft(f)
        val group = group(f, members = emptyList())
        var actor = f.actor
        var itemId = UUID.randomUUID()
        if (mode in setOf("inbox", "item", "read", "ack")) {
            val recipient = member(f)
            published(f, id)
            itemId = UUID.fromString(inbox(f, recipient)[0]["id"].asString())
            actor =
                f.actor.copy(
                    accountId = recipient.account,
                    permissions = setOf("announcements.read"),
                    credentialVersion = 0,
                )
        }
        if (mode == "return") {
            ok(publish(f, id))
            val lease = claim().single()
            val result = advance.execute(f.actor, lease)
            failure(result, "announcement_audience_empty")
            assertEquals(
                Result.Success(Unit),
                abort.execute(lease, (result as Result.Failed).failure),
            )
        }
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        val operation = UUID.randomUUID()
        val invoke: (Actor) -> Result<*> =
            when (mode) {
                "announcements" -> { current ->
                        ListAnnouncements(
                                announcements,
                                companies,
                                members,
                                identities,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current)
                    }
                "message",
                "message_revision" -> { current ->
                        GetAnnouncement(
                                announcements,
                                companies,
                                members,
                                identities,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current, id, if (mode == "message_revision") 0 else null)
                    }
                "history" -> { current ->
                        ListAnnouncementHistory(
                                announcements,
                                companies,
                                members,
                                identities,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current, id)
                    }
                "groups" -> { current ->
                        ListAudienceGroups(
                                groups,
                                companies,
                                members,
                                identities,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current)
                    }
                "group",
                "group_revision" -> { current ->
                        GetAudienceGroup(
                                groups,
                                companies,
                                members,
                                identities,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current, group, if (mode == "group_revision") 0 else null)
                    }
                "preview" -> { current ->
                        PreviewAnnouncementAudience(
                                announcements,
                                groups,
                                recipients,
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
                    }
                "save" -> { current ->
                        SaveAnnouncement(
                                announcements,
                                groups,
                                organization,
                                companies,
                                members,
                                identities,
                                operations,
                                journal,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(
                                current,
                                operation,
                                SaveAnnouncementCommand(
                                    id,
                                    0,
                                    "Reviewed notice",
                                    "Reviewed office information.",
                                    AnnouncementAudience(AudienceKind.COMPANY, emptyList()),
                                    true,
                                    "Assurance review",
                                ),
                            )
                    }
                "save_group" -> { current ->
                        SaveAudienceGroup(
                                groups,
                                people,
                                companies,
                                members,
                                identities,
                                operations,
                                journal,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(
                                current,
                                operation,
                                SaveAudienceGroupCommand(
                                    group,
                                    0,
                                    "Reviewed group",
                                    true,
                                    emptyList(),
                                    "Assurance review",
                                ),
                            )
                    }
                "publication_review" -> { current ->
                        GetAnnouncementReview(
                                announcements,
                                jobs,
                                companies,
                                members,
                                identities,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current, id)
                    }
                "publish" -> { current ->
                        QueueAnnouncement(
                                announcements,
                                groups,
                                organization,
                                companies,
                                members,
                                identities,
                                jobs,
                                operations,
                                journal,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current, operation, id, 0, null, "Assurance review")
                    }
                "archive" -> { current ->
                        ArchiveAnnouncement(
                                announcements,
                                inboxRepository,
                                jobs,
                                companies,
                                members,
                                identities,
                                operations,
                                journal,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current, operation, id, 0, "Assurance review")
                    }
                "return" -> { current ->
                        ReturnAnnouncementToDraft(
                                announcements,
                                jobs,
                                companies,
                                members,
                                identities,
                                operations,
                                journal,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current, operation, id, 1, "Assurance review")
                    }
                "inbox" -> { current ->
                        ListInbox(
                                inboxRepository,
                                announcements,
                                companies,
                                members,
                                identities,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current)
                    }
                "item" -> { current ->
                        GetInboxItem(
                                inboxRepository,
                                announcements,
                                companies,
                                members,
                                identities,
                                transactions,
                                clock,
                                security,
                            )
                            .execute(current, itemId)
                    }
                "read",
                "ack" -> { current ->
                        UpdateInboxItem(
                                inboxRepository,
                                announcements,
                                companies,
                                members,
                                identities,
                                transactions,
                                operations,
                                journal,
                                clock,
                                security,
                            )
                            .execute(
                                current,
                                operation,
                                itemId,
                                0,
                                if (mode == "read") InboxAction.READ else InboxAction.ACKNOWLEDGE,
                            )
                    }
                else -> error("Unknown test operation")
            }
        val initial = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(initial is Result.Success, initial.toString())
        val recorded =
            listOf(
                    "announcement_revisions",
                    "audience_group_revisions",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                )
                .associateWith { count(f, it) }
        val expiring =
            actor.copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<*>> { invoke(expiring) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                } finally {
                    barrier.release.countDown()
                }
                failure(pending.get(15, TimeUnit.SECONDS), "mfa_required")
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
        for ((table, before) in recorded) assertEquals(before, count(f, table), table)
        val renewed = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(renewed is Result.Success, renewed.toString())
        val originalReceipt = (initial as Result.Success).value as? MutationReceipt
        if (originalReceipt != null) {
            assertEquals(Result.Success(originalReceipt.copy(replayed = true)), renewed)
            for ((table, before) in recorded) assertEquals(before, count(f, table), table)
        }
    }

    @Test
    fun publicationReviewRechecksAssuranceAfterReadingItsJob() {
        val f = fixture()
        val id = draft(f)
        ok(publish(f, id, scheduledFor = clock.instant().plusSeconds(900)))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val delayed =
            object : JobRepository by jobs {
                override fun find(
                    companyId: UUID,
                    id: UUID,
                    lock: Boolean,
                ): Result<BackgroundJob?> {
                    val result = jobs.find(companyId, id, lock)
                    clock.set(clock.instant().plusSeconds(2))
                    return result
                }
            }
        val review =
            GetAnnouncementReview(
                announcements,
                delayed,
                companies,
                members,
                identities,
                transactions,
                clock,
                security,
            )
        val before = count(f, "audit_entries")
        failure(review.execute(actor, id), "mfa_required")
        assertEquals(before, count(f, "audit_entries"))
        assertTrue(
            review.execute(actor.copy(mfaVerifiedAt = clock.instant()), id) is Result.Success
        )
    }

    @Test
    fun expirationWhileWaitingForQueueAdmissionCannotCreateAJobOrConsumeAnOperation() {
        val f = fixture()
        val id = draft(f)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val delayedQueue =
            object : JobRepository by jobs {
                override fun lockQueue(companyId: UUID): Result<Unit> {
                    val result = jobs.lockQueue(companyId)
                    clock.set(clock.instant().plusSeconds(2))
                    return result
                }
            }
        val publisher =
            QueueAnnouncement(
                announcements,
                groups,
                organization,
                companies,
                members,
                identities,
                delayedQueue,
                operations,
                journal,
                transactions,
                clock,
                security,
            )
        val operation = UUID.randomUUID()
        failure(
            publisher.execute(actor, operation, id, 0, null, "Queue admission review"),
            "mfa_required",
        )
        assertEquals(0, count(f, "background_jobs"))
        assertEquals(1, count(f, "announcement_revisions"))
        val retried =
            publisher.execute(
                actor.copy(mfaVerifiedAt = clock.instant()),
                operation,
                id,
                0,
                null,
                "Queue admission review",
            )
        assertTrue(retried is Result.Success, retried.toString())
        assertEquals(1, count(f, "background_jobs"))
    }
}
