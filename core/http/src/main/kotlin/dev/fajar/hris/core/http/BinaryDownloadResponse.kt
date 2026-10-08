package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.Result
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ContentDisposition

/** HTTP negotiation only; the caller must authorize metadata and each content read. */
fun writeBinaryDownloadResponse(
    request: HttpServletRequest,
    response: HttpServletResponse,
    content: BinaryDownloadMetadata,
    writer: BinaryResponseWriter,
    blockSize: Int,
    read: (Long, Int) -> Result<ByteArray>,
) {
    require(content.size in 1L..104857600L && blockSize in 1..1048576)
    response.setHeader("ETag", content.etag)
    response.setHeader("Cache-Control", "private, no-store")
    response.setHeader("X-Content-Type-Options", "nosniff")
    response.setHeader("Accept-Ranges", "bytes")
    val noneMatch = request.getHeader("If-None-Match")
    if (noneMatch != null && matchesDownloadEtag(noneMatch, content.etag)) {
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
        request.method == "GET" && supplied != null && (ifRange == null || ifRange == content.etag)
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
    writer.write(request, response, range, blockSize, read)
}
