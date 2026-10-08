package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.documents.domain.policies.DOCUMENT_CHUNK_BYTES
import dev.fajar.hris.expenses.domain.usecases.GetExpenseReceiptDownload
import dev.fajar.hris.expenses.domain.usecases.ReadExpenseReceiptContent
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/submissions/{submissionId}/receipts")
class ExpenseReceiptController(
    private val get: GetExpenseReceiptDownload,
    private val read: ReadExpenseReceiptContent,
    private val writer: BinaryResponseWriter,
) {
    @GetMapping("/{revisionId}/content")
    fun download(
        actor: Actor,
        @PathVariable submissionId: UUID,
        @PathVariable revisionId: UUID,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val content = get.execute(actor, submissionId, revisionId).response()
        writeBinaryDownloadResponse(
            request,
            response,
            BinaryDownloadMetadata(
                content.fileName,
                content.mediaType,
                content.size,
                "\"${content.revisionId}-${content.sha256}\"",
            ),
            writer,
            DOCUMENT_CHUNK_BYTES,
        ) { offset, length ->
            read.execute(actor, submissionId, revisionId, offset, length)
        }
    }
}
