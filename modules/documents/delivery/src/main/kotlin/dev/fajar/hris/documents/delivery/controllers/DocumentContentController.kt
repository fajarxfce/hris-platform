package dev.fajar.hris.documents.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.documents.domain.policies.DOCUMENT_CHUNK_BYTES
import dev.fajar.hris.documents.domain.usecases.GetDocumentDownload
import dev.fajar.hris.documents.domain.usecases.ReadDocumentContent
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.http.ContentDisposition
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/documents/revisions")
class DocumentContentController(
    private val get: GetDocumentDownload,
    private val read: ReadDocumentContent,
    private val writer: BinaryResponseWriter,
) {
    @GetMapping("/{revisionId}/content")
    fun download(
        actor: Actor,
        @PathVariable revisionId: UUID,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val content = get.execute(actor, revisionId).response()
        val etag = "\"${content.revisionId}-${content.sha256}\""
        response.setHeader("ETag", etag)
        response.setHeader("Cache-Control", "private, no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Accept-Ranges", "bytes")
        val noneMatch = request.getHeader("If-None-Match")
        if (noneMatch != null && matchesDownloadEtag(noneMatch, etag)) {
            response.status = 304
            return
        }
        response.contentType = content.mediaType
        response.setHeader(
            "Content-Disposition",
            ContentDisposition.attachment()
                .filename(content.fileName, Charsets.UTF_8)
                .build()
                .toString(),
        )
        val supplied = request.getHeader("Range")
        val ifRange = request.getHeader("If-Range")
        val ranged =
            request.method == "GET" && supplied != null && (ifRange == null || ifRange == etag)
        val range =
            if (ranged) parseByteRange(requireNotNull(supplied), content.size)
            else ByteRange(0, content.size - 1)
        if (range == null) {
            response.status = 416
            response.setHeader("Content-Range", "bytes */${content.size}")
            response.setContentLength(0)
            return
        }
        response.status = if (ranged) 206 else 200
        if (ranged)
            response.setHeader("Content-Range", "bytes ${range.start}-${range.end}/${content.size}")
        response.setContentLengthLong(range.length)
        if (request.method == "HEAD") return
        writer.write(request, response, range, DOCUMENT_CHUNK_BYTES) { offset, length ->
            read.execute(actor, revisionId, offset, length)
        }
    }
}
