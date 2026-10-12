package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.storage.domain.repositories.*
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
abstract class DocumentAssuranceApiFixture : DocumentValidationApiFixture() {
    @Autowired protected lateinit var documents: DocumentRepository
    @Autowired protected lateinit var profiles: PersonProfileRepository
    @Autowired protected lateinit var people: PeopleRepository
    @Autowired protected lateinit var members: MembershipRepository
    @Autowired protected lateinit var identities: IdentityRepository
    @Autowired protected lateinit var companies: CompanyRepository
    @Autowired protected lateinit var jobs: JobRepository
    @Autowired protected lateinit var retention: DocumentRetentionRepository
    @Autowired protected lateinit var storage: ObjectStorageRepository
    @Autowired protected lateinit var cleanup: ObjectCleanupRepository
    @Autowired protected lateinit var operations: OperationRepository
    @Autowired protected lateinit var journal: ChangeJournalRepository
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    private val enrollments = mutableMapOf<UUID, String?>()

    @AfterEach
    fun restoreAssuranceFixture() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
        enrollments.forEach { (account, secret) ->
            database()
                .update("update accounts set mfa_secret_encrypted=? where id=?", secret, account)
        }
        enrollments.clear()
    }

    protected fun ready(f: Fixture, document: UUID): UUID {
        val revision = filled(f, document = document)
        assertTrue(run(f, beginValidation(f, revision)) is Result.Success)
        storageProbe.reads.set(0)
        return revision
    }

    protected fun enrolled(f: Fixture, security: IdentitySecurityPolicy): Actor {
        val account = f.actor.accountId
        if (!enrollments.containsKey(account))
            enrollments[account] =
                database()
                    .queryForObject(
                        "select mfa_secret_encrypted from accounts where id=?",
                        String::class.java,
                        account,
                    )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        return f.actor.copy(
            credentialVersion =
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Long::class.java,
                        account,
                    ),
            authenticatedAt = clock.instant(),
            mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
        )
    }

    protected fun reader(
        operation: String,
        f: Fixture,
        document: UUID,
        revision: UUID,
        security: IdentitySecurityPolicy,
    ): (Actor) -> Result<*> = { actor ->
        when (operation) {
            "header" ->
                GetDocument(
                        documents,
                        profiles,
                        people,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, document)
            "revision" ->
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
                    .execute(actor, revision)
            "directory" ->
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
                    .execute(actor, f.employee, null, 20)
            "history" ->
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
                    .execute(actor, document, null, 20)
            "attempts" ->
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
                    .execute(actor, revision)
            "download" ->
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
                    .execute(actor, revision)
            "content",
            "content-after-storage" ->
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
                    .execute(actor, revision, 0, pdf.size)
            else -> error("Unknown test read")
        }
    }

    protected fun mutation(
        operation: String,
        f: Fixture,
        revision: UUID,
        version: Long,
        operationId: UUID,
        security: IdentitySecurityPolicy,
    ): (Actor) -> Result<MutationReceipt> {
        val input =
            StartDocumentUploadCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                f.employee,
                "Evidence",
                DocumentClassification.PERSONAL,
                0,
                "evidence.pdf",
                "application/pdf",
                pdf.size.toLong(),
                digest(pdf),
                "Document submission",
            )
        return { actor ->
            when (operation) {
                "start" ->
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
                        .execute(actor, operationId, input)
                "chunk" ->
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
                        .execute(actor, operationId, revision, 0, digest(pdf), pdf)
                "cancel" ->
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
                        .execute(actor, operationId, revision, version, "Replacement required")
                "validate" ->
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
                        .execute(actor, operationId, revision, version, "Content review")
                else -> error("Unknown test mutation")
            }
        }
    }
}
