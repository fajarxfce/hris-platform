package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentMetadataRepository
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class DocumentMetadataAssuranceTest : DocumentAssuranceApiFixture() {
    @Autowired private lateinit var metadata: DocumentMetadataRepository
    private val value = DocumentBusinessMetadata(DocumentCategory.PASSPORT, "F-123", "AU")

    private fun save(
        journalSource: ChangeJournalRepository = journal,
        profileSource: PersonProfileRepository = profiles,
        security: IdentitySecurityPolicy = IdentitySecurityPolicy(enforceMfa = true),
    ) =
        SaveDocumentMetadata(
            metadata,
            documents,
            retention,
            profileSource,
            people,
            members,
            identities,
            operations,
            journalSource,
            transactions,
            clock,
            security,
        )

    private fun rows(company: UUID) =
        listOf(
                "document_metadata_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )
            .associateWith {
                database()
                    .queryForObject(
                        "select count(*) from $it where company_id=?",
                        Long::class.java,
                        company,
                    )
            }

    @Test
    fun expiryAfterActualJournalWriteRollsBackHeadHistoryOutboxReceiptAndSync() {
        val f = fixture()
        val id = UUID.randomUUID()
        ready(f, id)
        val expected =
            database()
                .queryForObject(
                    "select version from documents where company_id=? and id=?",
                    Long::class.java,
                    f.company,
                    id,
                )!!
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val original = clock.instant()
        val actor = enrolled(f, security)
        val before = rows(f.company)
        var writes = 0
        val late =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                    val result = journal.record(actor, change)
                    assertTrue(result is Result.Success, result.toString())
                    writes++
                    clock.set(original.plusSeconds(2))
                    return result
                }
            }
        val usecase = save(journalSource = late, security = security)
        val key = UUID.randomUUID()
        val rejected = usecase.execute(actor, key, id, expected, value, "Reviewed")
        assertEquals(
            "mfa_required",
            (rejected as? Result.Failed)?.failure?.code,
            rejected.toString(),
        )
        assertEquals(1, writes)
        assertEquals(before, rows(f.company))
        assertEquals(
            expected,
            database()
                .queryForObject(
                    "select version from documents where company_id=? and id=?",
                    Long::class.java,
                    f.company,
                    id,
                ),
        )
        val recovered =
            usecase.execute(
                actor.copy(mfaVerifiedAt = clock.instant()),
                key,
                id,
                expected,
                value,
                "Reviewed",
            )
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(2, writes)
        assertEquals(expected + 1, (recovered as Result.Success).value.version)
    }

    @ParameterizedTest
    @ValueSource(strings = ["history", "receipt"])
    fun dataAcquisitionCannotReleaseHistoryOrAnOldReceiptAfterMfaExpires(mode: String) {
        val f = fixture()
        val id = UUID.randomUUID()
        begin(f, document = id)
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val original = clock.instant()
        val actor = enrolled(f, security)
        val key = UUID.randomUUID()
        assertTrue(save().execute(actor, key, id, 1, value, "Reviewed") is Result.Success)
        val before = rows(f.company)
        var acquisitions = 0
        val lateProfiles =
            object : PersonProfileRepository by profiles {
                override fun findForEmployee(companyId: UUID, employeeId: UUID) =
                    profiles.findForEmployee(companyId, employeeId).also {
                        assertTrue(it is Result.Success)
                        acquisitions++
                        clock.set(original.plusSeconds(2))
                    }
            }
        val lateMetadata =
            object : DocumentMetadataRepository by metadata {
                override fun history(companyId: UUID, documentId: UUID, after: Long?, limit: Int) =
                    metadata.history(companyId, documentId, after, limit).also {
                        assertTrue(it is Result.Success)
                        acquisitions++
                        clock.set(original.plusSeconds(2))
                    }
            }
        fun invoke(current: Actor): Result<*> =
            if (mode == "receipt")
                save(profileSource = lateProfiles).execute(current, key, id, 1, value, "Reviewed")
            else
                GetDocumentMetadataHistory(
                        lateMetadata,
                        documents,
                        profiles,
                        people,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(current, id, null, 20)
        val rejected = invoke(actor)
        assertEquals(
            "mfa_required",
            (rejected as? Result.Failed)?.failure?.code,
            rejected.toString(),
        )
        assertEquals(1, acquisitions)
        assertEquals(before, rows(f.company))
        assertTrue(invoke(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
        assertEquals(2, acquisitions)
        assertEquals(before, rows(f.company))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun newlyGrantedPermissionsCannotExpandAnAlreadyCapturedRequest(replay: Boolean) {
        val f = fixture()
        val id = UUID.randomUUID()
        begin(f, document = id)
        val security = IdentitySecurityPolicy(enforceMfa = false)
        val usecase = save(security = security)
        val key = UUID.randomUUID()
        if (replay)
            assertTrue(usecase.execute(f.actor, key, id, 1, value, "Reviewed") is Result.Success)
        val before = rows(f.company)
        val restricted = f.actor.copy(permissions = f.actor.permissions - "people.profile.manage")
        val rejected = usecase.execute(restricted, key, id, 1, value, "Reviewed")
        assertEquals("document_access_denied", (rejected as? Result.Failed)?.failure?.code)
        assertEquals(before, rows(f.company))
    }

    @Test
    fun currentEnrollmentIsRequiredEvenWhenTheRequestCarriesRecentMfaProof() {
        val f = fixture()
        val id = UUID.randomUUID()
        begin(f, document = id)
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor = enrolled(f, security).copy(mfaVerifiedAt = clock.instant())
        database()
            .update("update accounts set mfa_secret_encrypted=null where id=?", actor.accountId)
        val rejected = save().execute(actor, UUID.randomUUID(), id, 1, value, "Reviewed")
        assertEquals("mfa_setup_required", (rejected as? Result.Failed)?.failure?.code)
    }
}
