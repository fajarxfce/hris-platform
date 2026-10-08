package dev.fajar.hris.documents.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.*
import dev.fajar.hris.documents.data.repositories.StoredDocumentRepository
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import dev.fajar.hris.storage.domain.repositories.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class DocumentsConfiguration {
    @Bean fun documentSource(sql: DSLContext): DocumentDataSource = PostgresDocumentDataSource(sql)

    @Bean
    fun documents(source: DocumentDataSource): DocumentRepository = StoredDocumentRepository(source)

    @Bean
    fun startDocumentUpload(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        cleanup: ObjectCleanupRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        StartDocumentUpload(
            documents,
            profiles,
            companies,
            members,
            identities,
            cleanup,
            operations,
            journal,
            transactions,
            clock,
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
        )

    @Bean
    fun cancelDocumentUpload(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        cleanup: ObjectCleanupRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        CancelDocumentUpload(
            documents,
            profiles,
            companies,
            members,
            identities,
            cleanup,
            operations,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun getDocument(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = GetDocument(documents, profiles, identities, transactions)

    @Bean
    fun getDocumentRevision(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetDocumentRevision(documents, profiles, identities, transactions, clock)

    @Bean
    fun listDocuments(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListDocuments(documents, profiles, identities, transactions)

    @Bean
    fun getDocumentRevisions(
        documents: DocumentRepository,
        profiles: PersonProfileRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetDocumentRevisions(documents, profiles, identities, transactions, clock)
}
