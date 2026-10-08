package dev.fajar.hris.documents.domain

import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.storage.domain.entities.ObjectInventoryEntry
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentInventoryPolicyTest {
    private val now = Instant.parse("2026-10-09T01:00:00Z")
    private val cutoff = now.minusSeconds(86400)
    private val attempt = UUID.randomUUID()
    private val company = UUID.randomUUID()
    private val revision = UUID.randomUUID()
    private val key = "$company/$revision/$attempt"
    private val old = ObjectInventoryEntry(key, 12, "etag", cutoff.minusSeconds(1))
    private val base =
        DocumentInventoryReference(
            key,
            attempt,
            revision,
            12,
            attempt,
            DocumentRevisionStatus.UPLOADING,
            now.plusSeconds(60),
            false,
            0,
        )

    @Test
    fun acceptedHistoricalContentIsNeverGarbageEvenAfterExpiry() {
        assertEquals(
            DocumentInventoryDisposition.RETAINED,
            classifyDocumentInventoryEntry(
                old,
                base.copy(
                    revisionStatus = DocumentRevisionStatus.READY,
                    expiresAt = now.minusSeconds(864000),
                ),
                cutoff,
                now,
            ),
        )
        assertEquals(
            DocumentInventoryDisposition.ANOMALOUS,
            classifyDocumentInventoryEntry(
                old.copy(size = 13),
                base.copy(revisionStatus = DocumentRevisionStatus.READY),
                cutoff,
                now,
            ),
        )
    }

    @Test
    fun expiredCancelledAndSupersededAttemptsRequireAnOldMatchingObservation() {
        for (reference in
            listOf(
                base.copy(revisionStatus = DocumentRevisionStatus.CANCELLED),
                base.copy(expiresAt = now),
                base.copy(currentAttemptId = UUID.randomUUID()),
            )) {
            assertEquals(
                DocumentInventoryDisposition.SCHEDULE,
                classifyDocumentInventoryEntry(old, reference, cutoff, now),
            )
            assertEquals(
                DocumentInventoryDisposition.RETAINED,
                classifyDocumentInventoryEntry(
                    old.copy(modifiedAt = cutoff.plusSeconds(1)),
                    reference,
                    cutoff,
                    now,
                ),
            )
            assertEquals(
                DocumentInventoryDisposition.ANOMALOUS,
                classifyDocumentInventoryEntry(old.copy(size = 11), reference, cutoff, now),
            )
        }
        assertEquals(
            DocumentInventoryDisposition.RETAINED,
            classifyDocumentInventoryEntry(old, base, cutoff, now),
        )
    }

    @Test
    fun existingCleanupAndThreeRecoveriesAreNotSilentlyReset() {
        val reference = base.copy(revisionStatus = DocumentRevisionStatus.REJECTED)
        assertEquals(
            DocumentInventoryDisposition.QUEUED,
            classifyDocumentInventoryEntry(
                old,
                reference.copy(cleanupRegistered = true),
                cutoff,
                now,
            ),
        )
        assertEquals(
            DocumentInventoryDisposition.RECOVERY_EXHAUSTED,
            classifyDocumentInventoryEntry(old, reference.copy(recoveryCount = 3), cutoff, now),
        )
        assertEquals(
            DocumentInventoryDisposition.UNKNOWN,
            classifyDocumentInventoryEntry(old, null, cutoff, now),
        )
    }

    @Test
    fun arbitraryProviderKeysNeverBecomeDatabaseAttemptReferences() {
        val entries =
            listOf(
                old,
                old.copy(key = "$company/folder/"),
                old.copy(key = "$company/\u0000"),
                old.copy(key = "$company/観光"),
            )
        assertEquals(setOf(key), documentInventoryCandidateKeys(entries))
        assertEquals(
            DocumentInventoryCounts(retained = 1, unknown = 1, scheduled = 1),
            documentInventoryCounts(
                listOf(
                    DocumentInventoryDisposition.RETAINED,
                    DocumentInventoryDisposition.UNKNOWN,
                    DocumentInventoryDisposition.SCHEDULE,
                )
            ),
        )
        assertEquals(
            3,
            documentInventoryCounts(
                    listOf(
                        DocumentInventoryDisposition.RETAINED,
                        DocumentInventoryDisposition.UNKNOWN,
                        DocumentInventoryDisposition.SCHEDULE,
                    )
                )
                .scanned,
        )
    }

    @Test
    fun maintenanceCommandsRequireTheirOwnGrantAndRecentAuthenticationProof() {
        val actor =
            dev.fajar.hris.core.domain.Actor(
                UUID.randomUUID(),
                company,
                setOf("documents.inventory", "jobs.retry"),
                now,
                UUID.randomUUID(),
            )
        val mfa = dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy(enforceMfa = true)
        assertTrue(
            validateDocumentInventoryCommand(actor, now, mfa)
                is dev.fajar.hris.core.domain.Result.Failed
        )
        assertEquals(
            dev.fajar.hris.core.domain.Result.Success(Unit),
            validateDocumentInventoryCommand(actor.copy(mfaVerifiedAt = now), now, mfa),
        )
        assertTrue(
            validateDocumentInventoryCommand(
                actor.copy(permissions = setOf("documents.inventory"), mfaVerifiedAt = now),
                now,
                mfa,
            )
                is dev.fajar.hris.core.domain.Result.Failed
        )
        assertTrue(
            validateDocumentInventoryCommand(
                actor.copy(authenticatedAt = now.minusSeconds(601)),
                now,
                mfa.copy(enforceMfa = false),
            )
                is dev.fajar.hris.core.domain.Result.Failed
        )
    }
}
