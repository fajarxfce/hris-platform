package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import dev.fajar.hris.storage.domain.entities.ObjectCleanupRequest
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class DocumentCommandCompletionTest : DocumentAssuranceApiFixture() {
    @Autowired private lateinit var inventory: DocumentInventoryRepository
    @Autowired private lateinit var references: DocumentReferenceRepository
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

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "start",
                "chunk",
                "cancel",
                "validate",
                "inventory",
                "policy",
                "archive",
                "restore",
                "hold",
                "release_hold",
                "retire",
            ]
    )
    fun expiryAfterJournalRollsBackTheCommandAndItsOriginalOperationCanRecover(mode: String) =
        verifyCommandCompletion(mode, true)

    @ParameterizedTest
    @ValueSource(strings = ["policy", "hold", "retire"])
    fun currentEnrollmentIsRequiredForRetentionBeforeMutation(mode: String) =
        verifyCommandCompletion(mode, false)

    private fun verifyCommandCompletion(mode: String, hasEnrollment: Boolean) {
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
        val revision =
            when (mode) {
                "start",
                "inventory" -> UUID.randomUUID()
                "validate" -> filled(f, document = document)
                "policy",
                "chunk",
                "cancel" -> begin(f, pdf, document)
                else -> ready(f, document)
            }
        val policyId = UUID.randomUUID()
        fun prepare(path: String, input: Map<String, Any?>) {
            val response =
                command(
                    f.browser,
                    "${f.path}/$path",
                    json.writeValueAsString(input),
                    f.csrf,
                    UUID.randomUUID(),
                )
            assertEquals(200, response.statusCode(), response.body())
        }
        if (mode == "retire")
            prepare(
                "retention-policies",
                mapOf(
                    "policyId" to policyId,
                    "classification" to "PERSONAL",
                    "retentionDays" to 1,
                    "reason" to "Command completion fixture",
                ),
            )
        if (mode in setOf("retire", "restore"))
            prepare("$document/retention/archive", mapOf("reason" to "Command completion fixture"))
        if (mode == "release_hold")
            prepare("$document/retention/hold", mapOf("reason" to "Command completion fixture"))
        if (mode == "retire") clock.set(clock.instant().plusSeconds(86401))
        val version =
            if (mode in setOf("start", "inventory")) 0L
            else revision(f, revision)["version"].asLong()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val recent =
            mode in
                setOf("inventory", "policy", "archive", "restore", "hold", "release_hold", "retire")
        val original = clock.instant()
        val actor =
            enrolled(f, security)
                .copy(
                    mfaVerifiedAt =
                        original
                            .minus(
                                if (recent) security.recentAuthenticationAge
                                else security.maximumMfaAge
                            )
                            .plusSeconds(1)
                )
        if (!hasEnrollment)
            database()
                .update("update accounts set mfa_secret_encrypted=null where id=?", actor.accountId)
        val key = UUID.randomUUID()
        var failLate = true
        var journalWrites = 0
        val journalSource =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                    val result = journal.record(actor, change)
                    assertTrue(
                        result is Result.Success,
                        "Actual journal write must succeed before expiry",
                    )
                    journalWrites++
                    if (failLate) clock.set(original.plusSeconds(2))
                    return result
                }
            }
        val input =
            StartDocumentUploadCommand(
                document,
                revision,
                f.employee,
                "Evidence",
                DocumentClassification.PERSONAL,
                0,
                "evidence.pdf",
                "application/pdf",
                pdf.size.toLong(),
                digest(pdf),
                "Command completion fixture",
            )
        val inventoryId = UUID.randomUUID()
        inventoryCompanies += f.company
        fun invoke(current: Actor): Result<MutationReceipt> =
            when (mode) {
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
                            journalSource,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, key, input)
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
                            journalSource,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, key, revision, 0, digest(pdf), pdf)
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
                            journalSource,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, key, revision, version, "Command completion fixture")
                "validate" ->
                    StartDocumentValidation(
                            documents,
                            profiles,
                            companies,
                            members,
                            identities,
                            jobs,
                            operations,
                            journalSource,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, key, revision, version, "Command completion fixture")
                "inventory" ->
                    StartDocumentInventory(
                            inventory,
                            documents,
                            companies,
                            members,
                            identities,
                            jobs,
                            operations,
                            journalSource,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, key, inventoryId, null, "Command completion fixture")
                "policy" ->
                    SaveDocumentRetentionPolicy(
                            retention,
                            documents,
                            companies,
                            members,
                            identities,
                            operations,
                            journalSource,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(
                            current,
                            key,
                            policyId,
                            DocumentClassification.PERSONAL,
                            null,
                            30,
                            "Command completion fixture",
                        )
                "retire" ->
                    RetireDocumentRevision(
                            retention,
                            documents,
                            references,
                            cleanup,
                            companies,
                            members,
                            identities,
                            operations,
                            journalSource,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, key, revision, version, 0, "Command completion fixture")
                else ->
                    ChangeDocumentRetention(
                            retention,
                            documents,
                            companies,
                            members,
                            identities,
                            operations,
                            journalSource,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(
                            current,
                            key,
                            document,
                            when (mode) {
                                "archive" -> DocumentRetentionAction.ARCHIVE
                                "restore" -> DocumentRetentionAction.RESTORE
                                "hold" -> DocumentRetentionAction.PLACE_HOLD
                                else -> DocumentRetentionAction.RELEASE_HOLD
                            },
                            if (mode in setOf("restore", "release_hold")) 0 else null,
                            "Command completion fixture",
                        )
            }
        val tables =
            listOf(
                "documents",
                "document_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "background_jobs",
            )
        fun counts() =
            tables.associateWith {
                database()
                    .queryForObject(
                        "select count(*) from $it where company_id=?",
                        Long::class.java,
                        f.company,
                    )
            }
        fun revisions() =
            database()
                .queryForList(
                    "select id,version,status,uploaded_bytes from document_revisions where company_id=? order by id",
                    f.company,
                )
        fun retained() =
            transactions.run(f.actor) {
                retention.policies(f.company).flatMap { policies ->
                    retention.state(f.company, document).flatMap { state ->
                        documents.find(f.company, document).map { listOf(policies, state, it) }
                    }
                }
            }
        val beforeCounts = counts()
        val beforeRevisions = revisions()
        val beforeRetention = retained()
        val failed = invoke(actor)
        assertEquals(if (hasEnrollment) 1 else 0, journalWrites)
        assertEquals(
            if (!hasEnrollment) "mfa_setup_required"
            else if (recent) "recent_authentication_required" else "mfa_required",
            (failed as? Result.Failed)?.failure?.code,
            "Expired authority must roll back the completed journal and command",
        )
        assertEquals(beforeCounts, counts())
        assertEquals(beforeRevisions, revisions())
        assertEquals(beforeRetention, retained())
        failLate = false
        if (!hasEnrollment)
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    actor.accountId,
                )
        if (mode == "chunk") {
            expireLease(revision)
            clock.set(Instant.now().plusSeconds(5))
        }
        val fresh = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(fresh)
        assertTrue(accepted is Result.Success, accepted.toString())
        assertEquals(if (hasEnrollment) 2 else 1, journalWrites)
        val committed = counts()
        assertEquals(
            Result.Success((accepted as Result.Success).value.copy(replayed = true)),
            invoke(fresh),
        )
        assertEquals(committed, counts())
        assertEquals(if (hasEnrollment) 2 else 1, journalWrites)
        if (mode == "chunk") assertEquals(2, storageProbe.calls.get())
    }

    @ParameterizedTest
    @ValueSource(strings = ["start", "chunk", "cancel", "validate"])
    fun originalUploadReceiptsRequireAssuranceAfterOwnershipAcquisition(mode: String) {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision =
            when (mode) {
                "start" -> UUID.randomUUID()
                "validate" -> filled(f, document = document)
                else -> begin(f, pdf, document)
            }
        val version = if (mode == "start") 0 else revision(f, revision)["version"].asLong()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val original = clock.instant()
        val actor = enrolled(f, security).copy(mfaVerifiedAt = original)
        var expire = false
        var ownershipReads = 0
        val source =
            object : PersonProfileRepository by profiles {
                override fun findForEmployee(companyId: UUID, employeeId: UUID) =
                    profiles.findForEmployee(companyId, employeeId).also {
                        assertTrue(it is Result.Success)
                        ownershipReads++
                        if (expire) clock.set(original.plusSeconds(2))
                    }
            }
        val key = UUID.randomUUID()
        val input =
            StartDocumentUploadCommand(
                document,
                revision,
                f.employee,
                "Evidence",
                DocumentClassification.PERSONAL,
                0,
                "evidence.pdf",
                "application/pdf",
                pdf.size.toLong(),
                digest(pdf),
                "Receipt completion fixture",
            )
        fun invoke(current: Actor): Result<MutationReceipt> =
            when (mode) {
                "start" ->
                    StartDocumentUpload(
                            documents,
                            retention,
                            jobs,
                            source,
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
                        .execute(current, key, input)
                "chunk" ->
                    UploadDocumentChunk(
                            documents,
                            source,
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
                        .execute(current, key, revision, 0, digest(pdf), pdf)
                "cancel" ->
                    CancelDocumentUpload(
                            documents,
                            jobs,
                            source,
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
                        .execute(current, key, revision, version, "Receipt completion fixture")
                else ->
                    StartDocumentValidation(
                            documents,
                            source,
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
                        .execute(current, key, revision, version, "Receipt completion fixture")
            }
        val accepted = invoke(actor)
        assertTrue(accepted is Result.Success, accepted.toString())
        val initialReads = ownershipReads
        val writes = storageProbe.calls.get()
        val audit =
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=?",
                    Long::class.java,
                    f.company,
                )
        expire = true
        val oldProof =
            actor.copy(mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1))
        assertEquals(
            "mfa_required",
            (invoke(oldProof) as? Result.Failed)?.failure?.code,
            "Receipt acquisition must not outlive its MFA proof",
        )
        assertEquals(initialReads + 1, ownershipReads)
        assertEquals(writes, storageProbe.calls.get())
        assertEquals(
            Result.Success((accepted as Result.Success).value.copy(replayed = true)),
            invoke(actor.copy(mfaVerifiedAt = clock.instant())),
        )
        assertEquals(
            audit,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=?",
                    Long::class.java,
                    f.company,
                ),
        )
        assertEquals(writes, storageProbe.calls.get())
    }

    @Test
    fun inventoryReceiptStillRequiresRecentProofAfterAccessAcquisition() {
        val f = fixture()
        inventoryCompanies += f.company
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val original = clock.instant()
        val actor = enrolled(f, security).copy(mfaVerifiedAt = original)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        var expire = false
        var acquisitions = 0
        val source =
            object : IdentityRepository by identities {
                override fun access(accountId: UUID, companyId: UUID?) =
                    identities.access(accountId, companyId).also {
                        assertTrue(it is Result.Success)
                        acquisitions++
                        if (expire) clock.set(original.plusSeconds(2))
                    }
            }
        val useCase =
            StartDocumentInventory(
                inventory,
                documents,
                companies,
                members,
                source,
                jobs,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val accepted = useCase.execute(actor, key, id, null, "Inventory receipt fixture")
        assertTrue(accepted is Result.Success, accepted.toString())
        expire = true
        val older =
            actor.copy(
                mfaVerifiedAt = original.minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        assertEquals(
            "recent_authentication_required",
            (useCase.execute(older, key, id, null, "Inventory receipt fixture") as? Result.Failed)
                ?.failure
                ?.code,
        )
        assertEquals(2, acquisitions)
        assertEquals(
            Result.Success((accepted as Result.Success).value.copy(replayed = true)),
            useCase.execute(
                actor.copy(mfaVerifiedAt = clock.instant()),
                key,
                id,
                null,
                "Inventory receipt fixture",
            ),
        )
    }

    @Test
    fun expiredChunkPreparationRollsBackItsReservationWithoutWritingAnObject() {
        val f = fixture()
        val revision = begin(f, pdf)
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val original = clock.instant()
        val actor = enrolled(f, security)
        var expire = true
        var scheduled = 0
        val source =
            object : ObjectCleanupRepository by cleanup {
                override fun schedule(request: ObjectCleanupRequest) =
                    cleanup.schedule(request).also {
                        assertTrue(it is Result.Success)
                        scheduled++
                        if (expire) clock.set(original.plusSeconds(2))
                    }
            }
        val key = UUID.randomUUID()
        val useCase =
            UploadDocumentChunk(
                documents,
                profiles,
                companies,
                members,
                identities,
                storage,
                source,
                operations,
                journal,
                transactions,
                clock,
                security,
            )
        assertEquals(
            "mfa_required",
            (useCase.execute(actor, key, revision, 0, digest(pdf), pdf) as? Result.Failed)
                ?.failure
                ?.code,
        )
        assertEquals(1, scheduled)
        assertEquals(
            0,
            storageProbe.calls.get(),
            "An expired reservation must not start external I/O",
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from document_upload_chunks where revision_id=?",
                    Int::class.java,
                    revision,
                ),
        )
        assertEquals(0, garbage(revision))
        expire = false
        val accepted =
            useCase.execute(
                actor.copy(mfaVerifiedAt = clock.instant()),
                key,
                revision,
                0,
                digest(pdf),
                pdf,
            )
        assertTrue(accepted is Result.Success, accepted.toString())
        assertEquals(1, storageProbe.calls.get())
    }
}
