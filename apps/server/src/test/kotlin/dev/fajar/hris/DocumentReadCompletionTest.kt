package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.people.domain.entities.EmployeeVisibility
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class DocumentReadCompletionTest : SelfDocumentApiFixture() {
    @Autowired private lateinit var inventory: DocumentInventoryRepository
    private val inventoryCompanies = mutableSetOf<UUID>()

    @AfterEach
    fun stopOwnedInventoryJobs() {
        for (company in inventoryCompanies) database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and kind='DOCUMENT_INVENTORY' and status in ('QUEUED','RUNNING')",
                company,
            )
        inventoryCompanies.clear()
    }

    private inner class Completion(private val f: Fixture) {
        val security = IdentitySecurityPolicy(enforceMfa = true)
        private val original = clock.instant()
        private val actor = enrolled(f, security)
        var acquisitions = 0
        private val tables =
            listOf(
                "documents",
                "document_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )

        private fun counts() =
            tables.associateWith {
                database()
                    .queryForObject(
                        "select count(*) from $it where company_id=?",
                        Long::class.java,
                        f.company,
                    )
            }

        private fun revisions() =
            database()
                .queryForList(
                    "select id,version,status from document_revisions where company_id=? order by id",
                    f.company,
                )

        private val initialCounts = counts()
        private val initialRevisions = revisions()

        fun <T> acquired(result: Result<T>): Result<T> {
            assertTrue(result is Result.Success, "Actual repository acquisition must succeed")
            acquisitions++
            clock.set(original.plusSeconds(2))
            return result
        }

        fun verify(
            storageOnExpired: Int = 0,
            storagePerSuccess: Int = 0,
            read: (Actor) -> Result<*>,
        ) {
            storageProbe.reads.set(0)
            val first = read(actor.copy(mfaVerifiedAt = original))
            assertTrue(first is Result.Success, first.toString())
            assertEquals(1, acquisitions)
            assertEquals(storagePerSuccess, storageProbe.reads.get())
            clock.set(original)
            val expired = read(actor)
            assertEquals(2, acquisitions)
            assertEquals(
                "mfa_required",
                (expired as? Result.Failed)?.failure?.code,
                "Expired MFA must not release acquired document data",
            )
            assertEquals(
                storagePerSuccess + storageOnExpired,
                storageProbe.reads.get(),
                "Expired preparation must not start an object read",
            )
            val renewed = read(actor.copy(mfaVerifiedAt = clock.instant()))
            assertTrue(renewed is Result.Success, renewed.toString())
            assertEquals(3, acquisitions)
            assertEquals(storagePerSuccess * 2 + storageOnExpired, storageProbe.reads.get())
            if (storagePerSuccess == 1)
                assertArrayEquals(pdf, (renewed as Result.Success).value as ByteArray)
            assertEquals(initialCounts, counts())
            assertEquals(initialRevisions, revisions())
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "header",
                "directory",
                "history",
                "history_job",
                "attempts",
                "download",
                "content_preparation",
                "content_completion",
            ]
    )
    fun metadataAndBytesRequireCurrentAssuranceAfterTheirLastAcquisition(mode: String) {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision =
            if (mode == "history_job")
                filled(f, document = document).also { beginValidation(f, it) }
            else ready(f, document)
        val completion = Completion(f)
        var profileReads = 0
        val profileSource =
            object : PersonProfileRepository by profiles {
                override fun findForEmployee(companyId: UUID, employeeId: UUID) =
                    profiles.findForEmployee(companyId, employeeId).let {
                        profileReads++
                        if (
                            mode in setOf("header", "download") ||
                                (mode == "content_completion" && profileReads % 2 == 0)
                        )
                            completion.acquired(it)
                        else it
                    }
            }
        val source =
            object : DocumentRepository by documents {
                override fun list(
                    companyId: UUID,
                    employmentId: UUID,
                    classifications: Set<DocumentClassification>,
                    after: UUID?,
                    limit: Int,
                ) =
                    completion.acquired(
                        documents.list(companyId, employmentId, classifications, after, limit)
                    )

                override fun revisions(companyId: UUID, documentId: UUID, after: Int?, limit: Int) =
                    documents.revisions(companyId, documentId, after, limit).let {
                        if (mode == "history") completion.acquired(it) else it
                    }

                override fun validationAttempts(companyId: UUID, revisionId: UUID) =
                    completion.acquired(documents.validationAttempts(companyId, revisionId))

                override fun chunkAt(companyId: UUID, revisionId: UUID, ordinal: Int) =
                    documents.chunkAt(companyId, revisionId, ordinal).let {
                        if (mode == "content_preparation") completion.acquired(it) else it
                    }
            }
        val jobSource =
            object : JobRepository by jobs {
                override fun find(companyId: UUID, id: UUID, lock: Boolean) =
                    completion.acquired(jobs.find(companyId, id, lock))
            }
        completion.verify(
            storagePerSuccess = if (mode.startsWith("content_")) 1 else 0,
            storageOnExpired = if (mode == "content_completion") 1 else 0,
        ) { actor ->
            when (mode) {
                "header" ->
                    GetDocument(
                            source,
                            profileSource,
                            people,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, document)
                "directory" ->
                    ListDocuments(
                            source,
                            profiles,
                            people,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, f.employee, null, 20)
                "history",
                "history_job" ->
                    GetDocumentRevisions(
                            source,
                            jobSource,
                            profiles,
                            people,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, document, null, 20)
                "attempts" ->
                    GetDocumentValidationAttempts(
                            source,
                            profiles,
                            people,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, revision)
                "download" ->
                    GetDocumentDownload(
                            source,
                            profileSource,
                            people,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, revision)
                else ->
                    ReadDocumentContent(
                            source,
                            profileSource,
                            people,
                            members,
                            identities,
                            storage,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, revision, 0, pdf.size)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "retention",
                "retention_history",
                "policies",
                "policy_history",
                "inventory",
                "inventories",
                "inventory_attempts",
                "inventory_pages",
            ]
    )
    fun retainedAdministrationRequiresCurrentAssuranceAfterAcquisition(mode: String) {
        val base = fixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                base.company,
                base.actor.accountId,
            )
        val f =
            base.copy(
                actor =
                    base.actor.copy(permissions = base.actor.permissions + "documents.retention")
            )
        val document = UUID.randomUUID()
        begin(f, document = document)
        val policy = UUID.randomUUID()
        val saved =
            command(
                f.browser,
                "${f.path}/retention-policies",
                json.writeValueAsString(
                    mapOf(
                        "policyId" to policy,
                        "classification" to "PERSONAL",
                        "retentionDays" to 30,
                        "reason" to "Read completion fixture",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, saved.statusCode(), saved.body())
        val run = UUID.randomUUID()
        inventoryCompanies += f.company
        val started =
            command(
                f.browser,
                "${f.path}/inventory",
                json.writeValueAsString(
                    mapOf("runId" to run, "reason" to "Read completion fixture")
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, started.statusCode(), started.body())
        val completion = Completion(f)
        val retentionSource =
            object : DocumentRetentionRepository by retention {
                override fun state(companyId: UUID, documentId: UUID) =
                    completion.acquired(retention.state(companyId, documentId))

                override fun history(companyId: UUID, documentId: UUID, after: Long?, limit: Int) =
                    completion.acquired(retention.history(companyId, documentId, after, limit))

                override fun policies(companyId: UUID) =
                    completion.acquired(retention.policies(companyId))

                override fun policyHistory(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                    completion.acquired(retention.policyHistory(companyId, id, after, limit))
            }
        val inventorySource =
            object : DocumentInventoryRepository by inventory {
                override fun find(companyId: UUID, id: UUID) =
                    inventory.find(companyId, id).let {
                        if (mode == "inventory") completion.acquired(it) else it
                    }

                override fun list(companyId: UUID, after: UUID?, limit: Int) =
                    completion.acquired(inventory.list(companyId, after, limit))

                override fun attempts(companyId: UUID, id: UUID) =
                    completion.acquired(inventory.attempts(companyId, id))

                override fun pages(companyId: UUID, id: UUID, after: Int, limit: Int) =
                    completion.acquired(inventory.pages(companyId, id, after, limit))
            }
        completion.verify { actor ->
            when (mode) {
                "retention" ->
                    GetDocumentRetention(
                            retentionSource,
                            documents,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, document)
                "retention_history" ->
                    GetDocumentRetentionHistory(
                            retentionSource,
                            documents,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, document, null, 20)
                "policies" ->
                    GetDocumentRetentionPolicies(
                            retentionSource,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor)
                "policy_history" ->
                    GetDocumentRetentionPolicyHistory(
                            retentionSource,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, policy, null, 20)
                "inventory" ->
                    GetDocumentInventory(
                            inventorySource,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, run)
                "inventories" ->
                    ListDocumentInventories(
                            inventorySource,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, null, 20)
                "inventory_attempts" ->
                    GetDocumentInventoryAttempts(
                            inventorySource,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, run)
                else ->
                    GetDocumentInventoryPages(
                            inventorySource,
                            members,
                            identities,
                            transactions,
                            clock,
                            completion.security,
                        )
                        .execute(actor, run, 0, 20)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["employee_search", "employee_selected", "self_employments", "self_evidence"]
    )
    fun discoveryAndOwnedEvidenceRequireCurrentAssuranceAfterAcquisition(mode: String) {
        val selected = selfFixture()
        val f = if (mode.startsWith("self_")) selected.owner else selected.administrator
        val document = UUID.randomUUID()
        ready(selected.administrator, document)
        val completion = Completion(f)
        val peopleSource =
            object : PeopleRepository by people {
                override fun find(companyId: UUID, id: UUID, asOf: LocalDate) =
                    completion.acquired(people.find(companyId, id, asOf))

                override fun list(
                    companyId: UUID,
                    accountId: UUID,
                    visibility: EmployeeVisibility,
                    asOf: LocalDate,
                    accessAt: Instant,
                    query: String,
                    after: String?,
                    limit: Int,
                ) =
                    completion.acquired(
                        people.list(
                            companyId,
                            accountId,
                            visibility,
                            asOf,
                            accessAt,
                            query,
                            after,
                            limit,
                        )
                    )
            }
        val source =
            object : DocumentRepository by documents {
                override fun published(
                    companyId: UUID,
                    employmentId: UUID,
                    classification: DocumentClassification,
                    after: UUID?,
                    limit: Int,
                ) =
                    completion.acquired(
                        documents.published(companyId, employmentId, classification, after, limit)
                    )
            }
        completion.verify { actor ->
            when (mode) {
                "self_employments" ->
                    ListSelfDocumentEmployments(
                            peopleSource,
                            companies,
                            members,
                            identities,
                            transactions,
                            completion.security,
                            clock,
                        )
                        .execute(actor, null, 20)
                "self_evidence" ->
                    ListSelfDocumentEvidence(
                            source,
                            profiles,
                            people,
                            members,
                            identities,
                            transactions,
                            completion.security,
                            clock,
                        )
                        .execute(actor, f.employee, DocumentClassification.PERSONAL, null, 20)
                else ->
                    ListDocumentEmployees(
                            peopleSource,
                            companies,
                            members,
                            identities,
                            transactions,
                            completion.security,
                            clock,
                        )
                        .execute(
                            actor,
                            "",
                            null,
                            20,
                            if (mode == "employee_selected") f.employee else null,
                        )
            }
        }
    }
}
