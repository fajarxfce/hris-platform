package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.documents.domain.policies.DOCUMENT_CHUNK_BYTES
import dev.fajar.hris.leave.domain.usecases.GetLeaveAttachmentDownload
import dev.fajar.hris.leave.domain.usecases.ReadLeaveAttachmentContent
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/requests/{requestId}/attachments")
class LeaveAttachmentController(
    private val get: GetLeaveAttachmentDownload,
    private val read: ReadLeaveAttachmentContent,
    private val writer: BinaryResponseWriter,
) {
    @GetMapping("/{revisionId}/content")
    fun download(
        actor: Actor,
        @PathVariable requestId: UUID,
        @PathVariable revisionId: UUID,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val content = get.execute(actor, requestId, revisionId).response()
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
            read.execute(actor, requestId, revisionId, offset, length)
        }
    }
}
