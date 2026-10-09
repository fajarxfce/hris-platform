package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveAttachmentPolicyTest {
    private val employee = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val at = Instant.parse("2026-10-01T00:00:00Z")
    private val document =
        Document(
            UUID.randomUUID(),
            employee,
            "Employment evidence",
            DocumentClassification.PERSONAL,
            1,
            1,
            author,
            at,
        )
    private val revision =
        DocumentRevision(
            UUID.randomUUID(),
            document.id,
            1,
            "evidence.pdf",
            "application/pdf",
            12,
            "a".repeat(64),
            DocumentRevisionStatus.READY,
            12,
            3,
            author,
            at,
            at.plusSeconds(86400),
            "Evidence",
        )

    @Test
    fun identifiersAreBoundedAndDuplicatesAreRejectedWithoutSilentlyDroppingInput() {
        assertTrue(validateLeaveAttachmentIds(emptyList()) is Result.Success)
        assertTrue(validateLeaveAttachmentIds(List(3) { UUID.randomUUID() }) is Result.Success)
        val tooMany = validateLeaveAttachmentIds(List(4) { UUID.randomUUID() }) as Result.Failed
        assertEquals("3", tooMany.failure.parameters["maximum"])
        val duplicate =
            validateLeaveAttachmentIds(listOf(revision.id, revision.id)) as Result.Failed
        assertEquals("duplicate", duplicate.failure.fields["attachmentRevisionIds"])
    }

    @Test
    fun onlyAcceptedPersonalEvidenceForTheIntendedEmploymentCanBeSnapshotted() {
        for (candidate in
            listOf<Document?>(
                null,
                document.copy(employmentId = UUID.randomUUID()),
                document.copy(classification = DocumentClassification.HR_ONLY),
                document.copy(classification = DocumentClassification.RECEIPT),
                document.copy(id = UUID.randomUUID()),
            )) {
            val result = snapshotLeaveAttachment(candidate, revision, employee) as Result.Failed
            assertEquals("leave_attachment_unavailable", result.failure.code)
        }
        for (status in
            DocumentRevisionStatus.entries.filter {
                it != DocumentRevisionStatus.READY
            }) assertTrue(
            snapshotLeaveAttachment(document, revision.copy(status = status), employee)
                is Result.Failed
        )
        assertTrue(snapshotLeaveAttachment(document, null, employee) is Result.Failed)
    }

    @Test
    fun aSelectedAcceptedRevisionRemainsIndependentOfTheCurrentlyPublishedRevision() {
        val result =
            snapshotLeaveAttachment(
                document.copy(currentRevisionId = UUID.randomUUID(), revisionCount = 2),
                revision,
                employee,
            )
                as Result.Success
        assertEquals(revision.id, result.value.revisionId)
        assertEquals(revision.sha256, result.value.sha256)
        assertEquals(revision.fileName, result.value.fileName)
    }
}
