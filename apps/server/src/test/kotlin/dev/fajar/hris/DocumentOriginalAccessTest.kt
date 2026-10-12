package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class DocumentOriginalAccessTest : DocumentValidationApiFixture() {
    @Autowired private lateinit var header: GetDocument
    @Autowired private lateinit var details: GetDocumentRevision
    @Autowired private lateinit var directory: ListDocuments
    @Autowired private lateinit var history: GetDocumentRevisions
    @Autowired private lateinit var attempts: GetDocumentValidationAttempts
    @Autowired private lateinit var download: GetDocumentDownload
    @Autowired private lateinit var content: ReadDocumentContent
    @Autowired private lateinit var startUpload: StartDocumentUpload
    @Autowired private lateinit var uploadChunk: UploadDocumentChunk
    @Autowired private lateinit var cancelUpload: CancelDocumentUpload
    @Autowired private lateinit var startValidation: StartDocumentValidation

    @ParameterizedTest
    @ValueSource(
        strings = ["header", "revision", "directory", "history", "attempts", "download", "content"]
    )
    fun readsCannotGainPermissionsMissingFromTheResolvedRequest(operation: String) {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision = filled(f, document = document)
        assertEquals(Result.Success(JobStep(1, true)), run(f, beginValidation(f, revision)))
        storageProbe.reads.set(0)
        val actor = f.actor.copy(permissions = emptySet())
        val result =
            when (operation) {
                "header" -> header.execute(actor, document)
                "revision" -> details.execute(actor, revision)
                "directory" -> directory.execute(actor, f.employee, null, 20)
                "history" -> history.execute(actor, document, null, 20)
                "attempts" -> attempts.execute(actor, revision)
                "download" -> download.execute(actor, revision)
                "content" -> content.execute(actor, revision, 0, pdf.size)
                else -> error("Unknown test operation")
            }
        assertEquals("document_access_denied", (result as? Result.Failed)?.failure?.code)
        assertEquals(0, storageProbe.reads.get())
    }

    @ParameterizedTest
    @ValueSource(strings = ["start", "chunk", "cancel", "validate"])
    fun mutationsCannotUseGrantsAddedAfterTheRequestWasResolved(operation: String) {
        val f = fixture()
        val revision = if (operation == "validate") filled(f) else begin(f, pdf)
        val version = revision(f, revision).get("version").asLong()
        val actor = f.actor.copy(permissions = emptySet())
        val id = UUID.randomUUID()
        val result =
            when (operation) {
                "start" ->
                    startUpload.execute(
                        actor,
                        id,
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
                        ),
                    )
                "chunk" -> uploadChunk.execute(actor, id, revision, 0, digest(pdf), pdf)
                "cancel" ->
                    cancelUpload.execute(actor, id, revision, version, "Replacement required")
                "validate" ->
                    startValidation.execute(actor, id, revision, version, "Content review")
                else -> error("Unknown test operation")
            }
        assertEquals("document_access_denied", (result as? Result.Failed)?.failure?.code)
    }
}
