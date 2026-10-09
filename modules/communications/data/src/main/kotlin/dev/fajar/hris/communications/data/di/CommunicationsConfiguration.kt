package dev.fajar.hris.communications.data.di

import dev.fajar.hris.communications.data.datasources.*
import dev.fajar.hris.communications.data.repositories.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class CommunicationsConfiguration {
    @Bean
    fun announcementSource(sql: DSLContext): AnnouncementDataSource =
        PostgresAnnouncementDataSource(sql)

    @Bean
    fun audienceGroupSource(sql: DSLContext): AudienceGroupDataSource =
        PostgresAudienceGroupDataSource(sql)

    @Bean
    fun announcements(source: AnnouncementDataSource, json: ObjectMapper): AnnouncementRepository =
        StoredAnnouncementRepository(source, json)

    @Bean
    fun audienceGroups(
        source: AudienceGroupDataSource,
        json: ObjectMapper,
    ): AudienceGroupRepository = StoredAudienceGroupRepository(source, json)

    @Bean
    fun saveAnnouncement(
        announcements: AnnouncementRepository,
        groups: AudienceGroupRepository,
        organization: OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
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
        )

    @Bean
    fun saveAudienceGroup(
        groups: AudienceGroupRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
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
        )

    @Bean
    fun getAnnouncement(
        announcements: AnnouncementRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetAnnouncement(announcements, companies, members, identities, transactions)

    @Bean
    fun listAnnouncements(
        announcements: AnnouncementRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListAnnouncements(announcements, companies, members, identities, transactions)

    @Bean
    fun listAnnouncementHistory(
        announcements: AnnouncementRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListAnnouncementHistory(announcements, companies, members, identities, transactions)

    @Bean
    fun getAudienceGroup(
        groups: AudienceGroupRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetAudienceGroup(groups, companies, members, identities, transactions)

    @Bean
    fun listAudienceGroups(
        groups: AudienceGroupRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListAudienceGroups(groups, companies, members, identities, transactions)

    @Bean
    fun announcementAudienceSource(sql: DSLContext): AnnouncementAudienceDataSource =
        PostgresAnnouncementAudienceDataSource(sql)

    @Bean
    fun announcementPublicationSource(sql: DSLContext): AnnouncementPublicationDataSource =
        PostgresAnnouncementPublicationDataSource(sql)

    @Bean fun inboxSource(sql: DSLContext): InboxDataSource = PostgresInboxDataSource(sql)

    @Bean
    fun announcementAudience(
        source: AnnouncementAudienceDataSource
    ): AnnouncementAudienceRepository = StoredAnnouncementAudienceRepository(source)

    @Bean
    fun announcementPublications(
        source: AnnouncementPublicationDataSource,
        json: ObjectMapper,
    ): AnnouncementPublicationRepository = StoredAnnouncementPublicationRepository(source, json)

    @Bean fun inbox(source: InboxDataSource): InboxRepository = StoredInboxRepository(source)

    @Bean
    fun queueAnnouncement(
        announcements: AnnouncementRepository,
        groups: AudienceGroupRepository,
        organization: OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
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
        )

    @Bean
    fun advanceAnnouncementPublication(
        announcements: AnnouncementRepository,
        groups: AudienceGroupRepository,
        audience: AnnouncementAudienceRepository,
        publications: AnnouncementPublicationRepository,
        people: PeopleRepository,
        organization: OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        AdvanceAnnouncementPublication(
            announcements,
            groups,
            audience,
            publications,
            people,
            organization,
            companies,
            members,
            identities,
            jobs,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun abortAnnouncementPublication(
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AbortAnnouncementPublication(jobs, journal, transactions)

    @Bean
    fun returnAnnouncementToDraft(
        announcements: AnnouncementRepository,
        jobs: JobRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
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
        )

    @Bean
    fun archiveAnnouncement(
        announcements: AnnouncementRepository,
        inbox: InboxRepository,
        jobs: JobRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        ArchiveAnnouncement(
            announcements,
            inbox,
            jobs,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun getInboxItem(
        inbox: InboxRepository,
        announcements: AnnouncementRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetInboxItem(inbox, announcements, companies, members, identities, transactions)

    @Bean
    fun listInbox(
        inbox: InboxRepository,
        announcements: AnnouncementRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListInbox(inbox, announcements, companies, members, identities, transactions)

    @Bean
    fun updateInboxItem(
        inbox: InboxRepository,
        announcements: AnnouncementRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        clock: Clock,
    ) =
        UpdateInboxItem(
            inbox,
            announcements,
            companies,
            members,
            identities,
            transactions,
            operations,
            journal,
            clock,
        )

    @Bean
    fun previewAnnouncementAudience(
        announcements: AnnouncementRepository,
        groups: AudienceGroupRepository,
        audience: AnnouncementAudienceRepository,
        people: PeopleRepository,
        organization: OrganizationRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        PreviewAnnouncementAudience(
            announcements,
            groups,
            audience,
            people,
            organization,
            companies,
            members,
            identities,
            transactions,
            clock,
        )
}
