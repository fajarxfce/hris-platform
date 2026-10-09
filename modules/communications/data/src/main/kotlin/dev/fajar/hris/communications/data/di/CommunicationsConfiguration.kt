package dev.fajar.hris.communications.data.di

import dev.fajar.hris.communications.data.datasources.*
import dev.fajar.hris.communications.data.repositories.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
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
}
