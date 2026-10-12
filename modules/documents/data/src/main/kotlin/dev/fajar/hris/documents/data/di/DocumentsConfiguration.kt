package dev.fajar.hris.documents.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.*
import dev.fajar.hris.documents.data.repositories.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import dev.fajar.hris.storage.domain.repositories.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class DocumentsConfiguration {
    @Bean
    fun documentScanSource(
        environment: org.springframework.core.env.Environment
    ): DocumentScanDataSource {
        if (!environment.getProperty("HRIS_DOCUMENT_SCANNER_ENABLED", Boolean::class.java, false))
            return UnavailableDocumentScanDataSource()
        val host = environment.getRequiredProperty("HRIS_DOCUMENT_SCANNER_HOST")
        require(host.length in 1..253 && host.matches(Regex("[A-Za-z0-9_.:-]+"))) {
            "Invalid document scanner host"
        }
        val port = environment.getProperty("HRIS_DOCUMENT_SCANNER_PORT", Int::class.java, 3310)
        return ClamAvDocumentScanDataSource(ClamAvSettings(java.net.InetSocketAddress(host, port)))
    }

    @Bean fun documentMediaTypes(): DocumentMediaTypeDataSource = TikaDocumentMediaTypeDataSource()

    @Bean
    fun documentInspection(
        storage: dev.fajar.hris.storage.data.datasources.ObjectStorageDataSource,
        scanner: DocumentScanDataSource,
        types: DocumentMediaTypeDataSource,
    ): DocumentInspectionRepository = InspectedDocumentRepository(storage, scanner, types)

    @Bean fun documentSource(sql: DSLContext): DocumentDataSource = PostgresDocumentDataSource(sql)

    @Bean
    fun documents(source: DocumentDataSource): DocumentRepository = StoredDocumentRepository(source)

    @Bean
    fun startDocumentUpload(
        documents: DocumentRepository,
        retention: DocumentRetentionRepository,
        jobs: JobRepository,
        profiles: PersonProfileRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        cleanup: ObjectCleanupRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        StartDocumentUpload(
            documents,
            retention,
            jobs,
            profiles,
            companies,
            members,
            identities,
            cleanup,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun uploadDocumentChunk(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        storage: ObjectStorageRepository,
        cleanup: ObjectCleanupRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        UploadDocumentChunk(
            documents,
            profiles,
            companies,
            members,
            identities,
            storage,
            cleanup,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun cancelDocumentUpload(
        documents: DocumentRepository,
        jobs: JobRepository,
        profiles: PersonProfileRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        cleanup: ObjectCleanupRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        CancelDocumentUpload(
            documents,
            jobs,
            profiles,
            companies,
            members,
            identities,
            cleanup,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getDocument(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = GetDocument(documents, profiles, people, members, identities, transactions, clock, security)

    @Bean
    fun getDocumentRevision(
        documents: DocumentRepository,
        jobs: JobRepository,
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetDocumentRevision(
            documents,
            jobs,
            profiles,
            people,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun listDocuments(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ListDocuments(
            documents,
            profiles,
            people,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getDocumentRevisions(
        documents: DocumentRepository,
        jobs: JobRepository,
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetDocumentRevisions(
            documents,
            jobs,
            profiles,
            people,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun startDocumentValidation(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        StartDocumentValidation(
            documents,
            profiles,
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

    @Bean
    fun advanceDocumentValidation(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        inspection: DocumentInspectionRepository,
        cleanup: ObjectCleanupRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        AdvanceDocumentValidation(
            documents,
            profiles,
            companies,
            members,
            identities,
            jobs,
            inspection,
            cleanup,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun abortDocumentValidation(
        documents: DocumentRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AbortDocumentValidation(documents, jobs, journal, transactions)

    @Bean
    fun getDocumentValidationAttempts(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetDocumentValidationAttempts(
            documents,
            profiles,
            people,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getDocumentDownload(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetDocumentDownload(
            documents,
            profiles,
            people,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun readDocumentContent(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        people: PeopleRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        storage: ObjectStorageRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        ReadDocumentContent(
            documents,
            profiles,
            people,
            members,
            identities,
            storage,
            transactions,
            clock,
            security,
        )

    @Bean
    fun documentInventorySource(sql: DSLContext): DocumentInventoryDataSource =
        PostgresDocumentInventoryDataSource(sql)

    @Bean
    fun documentInventory(source: DocumentInventoryDataSource): DocumentInventoryRepository =
        StoredDocumentInventoryRepository(source)

    @Bean
    fun startDocumentInventory(
        inventory: DocumentInventoryRepository,
        documents: DocumentRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
        clock: Clock,
    ) =
        StartDocumentInventory(
            inventory,
            documents,
            companies,
            members,
            identities,
            jobs,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun advanceDocumentInventory(
        inventory: DocumentInventoryRepository,
        documents: DocumentRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        jobs: JobRepository,
        storage: ObjectStorageRepository,
        cleanup: ObjectCleanupRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        AdvanceDocumentInventory(
            inventory,
            documents,
            companies,
            members,
            identities,
            jobs,
            storage,
            cleanup,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun abortDocumentInventory(
        documents: DocumentRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AbortDocumentInventory(documents, jobs, journal, transactions)

    @Bean
    fun getDocumentInventory(
        inventory: DocumentInventoryRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = GetDocumentInventory(inventory, members, identities, transactions, clock, security)

    @Bean
    fun listDocumentInventories(
        inventory: DocumentInventoryRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = ListDocumentInventories(inventory, members, identities, transactions, clock, security)

    @Bean
    fun getDocumentInventoryAttempts(
        inventory: DocumentInventoryRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = GetDocumentInventoryAttempts(inventory, members, identities, transactions, clock, security)

    @Bean
    fun getDocumentInventoryPages(
        inventory: DocumentInventoryRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = GetDocumentInventoryPages(inventory, members, identities, transactions, clock, security)

    @Bean
    fun documentReferenceSource(sql: DSLContext): DocumentReferenceDataSource =
        PostgresDocumentReferenceDataSource(sql)

    @Bean
    fun documentReferences(source: DocumentReferenceDataSource): DocumentReferenceRepository =
        StoredDocumentReferenceRepository(source)

    @Bean
    fun documentRetentionSource(sql: DSLContext): DocumentRetentionDataSource =
        PostgresDocumentRetentionDataSource(sql)

    @Bean
    fun documentRetention(source: DocumentRetentionDataSource): DocumentRetentionRepository =
        StoredDocumentRetentionRepository(source)

    @Bean
    fun saveDocumentRetentionPolicy(
        retention: DocumentRetentionRepository,
        documents: DocumentRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
        clock: Clock,
    ) =
        SaveDocumentRetentionPolicy(
            retention,
            documents,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun changeDocumentRetention(
        retention: DocumentRetentionRepository,
        documents: DocumentRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ChangeDocumentRetention(
            retention,
            documents,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getDocumentRetentionPolicies(
        retention: DocumentRetentionRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) = GetDocumentRetentionPolicies(retention, members, identities, transactions, clock, security)

    @Bean
    fun getDocumentRetentionPolicyHistory(
        retention: DocumentRetentionRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetDocumentRetentionPolicyHistory(
            retention,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getDocumentRetention(
        retention: DocumentRetentionRepository,
        documents: DocumentRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetDocumentRetention(
            retention,
            documents,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun getDocumentRetentionHistory(
        retention: DocumentRetentionRepository,
        documents: DocumentRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetDocumentRetentionHistory(
            retention,
            documents,
            members,
            identities,
            transactions,
            clock,
            security,
        )

    @Bean
    fun retireDocumentRevision(
        retention: DocumentRetentionRepository,
        documents: DocumentRepository,
        references: DocumentReferenceRepository,
        cleanup: ObjectCleanupRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
        clock: Clock,
    ) =
        RetireDocumentRevision(
            retention,
            documents,
            references,
            cleanup,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            security,
            clock,
        )
}
