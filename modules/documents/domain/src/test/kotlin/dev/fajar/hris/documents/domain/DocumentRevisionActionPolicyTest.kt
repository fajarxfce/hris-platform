package dev.fajar.hris.documents.domain

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.documentRevisionActions
import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentRevisionActionPolicyTest {
    private val now = Instant.parse("2026-10-12T10:00:00Z")
    private val actor =
        Actor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            setOf("documents.manage", "people.profile.manage"),
            now,
            UUID.randomUUID(),
        )
    private val revision =
        DocumentRevision(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            "record.pdf",
            "application/pdf",
            100,
            "a".repeat(64),
            DocumentRevisionStatus.UPLOADING,
            100,
            1,
            actor.accountId,
            now,
            now.plusSeconds(86400),
            "Evidence",
        )
    private val both = listOf(DocumentRevisionAction.VALIDATE, DocumentRevisionAction.CANCEL_UPLOAD)
    private val cancel = listOf(DocumentRevisionAction.CANCEL_UPLOAD)

    @Test
    fun managementNeedsBothCompanyGrantsOrTheOwnedSelfGrant() {
        for (permissions in
            listOf(
                emptySet(),
                setOf("documents.read", "people.profile.read"),
                setOf("documents.manage"),
                setOf("people.profile.manage"),
            )) assertTrue(
            documentRevisionActions(
                    actor.copy(permissions = permissions),
                    DocumentClassification.PERSONAL,
                    null,
                    revision,
                    null,
                    now,
                )
                .isEmpty()
        )
        assertEquals(
            both,
            documentRevisionActions(
                actor,
                DocumentClassification.HR_ONLY,
                null,
                revision,
                null,
                now,
            ),
        )
        val owner = actor.copy(permissions = setOf("documents.self.upload"))
        assertEquals(
            both,
            documentRevisionActions(
                owner,
                DocumentClassification.PERSONAL,
                actor.accountId,
                revision,
                null,
                now,
            ),
        )
        assertTrue(
            documentRevisionActions(
                    owner,
                    DocumentClassification.HR_ONLY,
                    actor.accountId,
                    revision,
                    null,
                    now,
                )
                .isEmpty()
        )
        assertTrue(
            documentRevisionActions(
                    owner,
                    DocumentClassification.PERSONAL,
                    UUID.randomUUID(),
                    revision,
                    null,
                    now,
                )
                .isEmpty()
        )
    }

    @Test
    fun incompleteOrExhaustedContentCannotStartValidation() {
        for (value in
            listOf(
                revision.copy(uploadedBytes = 0),
                revision.copy(validationAttempts = 8),
            )) assertEquals(
            cancel,
            documentRevisionActions(actor, DocumentClassification.PERSONAL, null, value, null, now),
        )
    }

    @Test
    fun expiryPreservesExplicitCancellationOnlyForPersistedActiveUploads() {
        val expired = revision.copy(expiresAt = now)
        assertEquals(
            cancel,
            documentRevisionActions(
                actor,
                DocumentClassification.PERSONAL,
                null,
                expired,
                null,
                now,
            ),
        )
        for (status in
            listOf(
                DocumentRevisionStatus.EXPIRED,
                DocumentRevisionStatus.CANCELLED,
                DocumentRevisionStatus.READY,
                DocumentRevisionStatus.REJECTED,
                DocumentRevisionStatus.RETIRED,
            )) assertTrue(
            documentRevisionActions(
                    actor,
                    DocumentClassification.PERSONAL,
                    null,
                    expired.copy(status = status),
                    null,
                    now,
                )
                .isEmpty()
        )
    }

    @Test
    fun validationRecoveryRequiresAStoppedPriorJob() {
        val validating =
            revision.copy(
                status = DocumentRevisionStatus.VALIDATING,
                validationJobId = UUID.randomUUID(),
                validationAttempts = 1,
            )
        for (status in JobStatus.entries) {
            val expected =
                if (status in setOf(JobStatus.FAILED, JobStatus.CANCELLED)) both else cancel
            assertEquals(
                expected,
                documentRevisionActions(
                    actor,
                    DocumentClassification.PERSONAL,
                    null,
                    validating,
                    status,
                    now,
                ),
            )
        }
        assertEquals(
            cancel,
            documentRevisionActions(
                actor,
                DocumentClassification.PERSONAL,
                null,
                validating,
                null,
                now,
            ),
        )
    }
}
