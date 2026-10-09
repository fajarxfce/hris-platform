package dev.fajar.hris.documents.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.storage.domain.entities.ObjectInventoryEntry
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentRetirementPolicyTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val doc = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val revision =
        DocumentRevision(
            id,
            doc,
            1,
            "evidence.pdf",
            "application/pdf",
            100,
            "a".repeat(64),
            DocumentRevisionStatus.READY,
            100,
            3,
            actor,
            now.minusSeconds(172800),
            now.minusSeconds(86400),
            "Evidence",
        )
    private val state =
        DocumentRetentionState(
            doc,
            0,
            DocumentArchive(now.minusSeconds(86400), UUID.randomUUID(), 0, 1, now),
        )

    @Test
    fun onlyAnUnreferencedEligibleAcceptedRevisionCanBeRetired() {
        assertEquals(
            Result.Success(state.copy(version = 1)),
            retireDocumentState(state, revision, false, now),
        )
        assertTrue(retireDocumentState(state, revision, true, now) is Result.Failed)
        assertTrue(retireDocumentState(state, revision, false, now.minusNanos(1)) is Result.Failed)
        assertTrue(
            retireDocumentState(
                state,
                revision.copy(status = DocumentRevisionStatus.RETIRED),
                false,
                now,
            )
                is Result.Failed
        )
    }

    @Test
    fun missingArchivesIndefiniteRetentionAndHoldsAreNotDeleteInstructions() {
        assertTrue(
            retireDocumentState(state.copy(archive = null), revision, false, now) is Result.Failed
        )
        assertTrue(
            retireDocumentState(
                state.copy(archive = state.archive!!.copy(retentionDays = null, eligibleAt = null)),
                revision,
                false,
                now,
            )
                is Result.Failed
        )
        assertTrue(
            retireDocumentState(state.copy(legalHold = true), revision, false, now) is Result.Failed
        )
        assertTrue(
            retireDocumentState(state.copy(version = 9999), revision, false, now) is Result.Failed
        )
    }

    @Test
    fun genericLifecycleChangesCannotSkipTheRetirementUseCase() {
        assertTrue(
            transitionDocumentRetention(state, DocumentRetentionAction.RETIRE, null, now)
                is Result.Failed
        )
        assertTrue(
            retireDocumentState(state.copy(documentId = UUID.randomUUID()), revision, false, now)
                is Result.Failed
        )
    }

    @Test
    fun retiredObjectsStillRequireConservativeInventoryEvidence() {
        val company = UUID.randomUUID()
        val attempt = UUID.randomUUID()
        val key = "$company/$id/$attempt"
        val reference =
            DocumentInventoryReference(
                key,
                attempt,
                id,
                100,
                attempt,
                DocumentRevisionStatus.RETIRED,
                now.minusSeconds(86400),
                false,
                0,
            )
        val old = ObjectInventoryEntry(key, 100, "tag", now.minusSeconds(172800))
        assertEquals(
            DocumentInventoryDisposition.SCHEDULE,
            classifyDocumentInventoryEntry(old, reference, now.minusSeconds(86400), now),
        )
        assertEquals(
            DocumentInventoryDisposition.RETAINED,
            classifyDocumentInventoryEntry(
                old.copy(modifiedAt = now),
                reference,
                now.minusSeconds(86400),
                now,
            ),
        )
        assertEquals(
            DocumentInventoryDisposition.QUEUED,
            classifyDocumentInventoryEntry(
                old,
                reference.copy(cleanupRegistered = true),
                now.minusSeconds(86400),
                now,
            ),
        )
        assertEquals(
            DocumentInventoryDisposition.RECOVERY_EXHAUSTED,
            classifyDocumentInventoryEntry(
                old,
                reference.copy(recoveryCount = 3),
                now.minusSeconds(86400),
                now,
            ),
        )
    }
}
