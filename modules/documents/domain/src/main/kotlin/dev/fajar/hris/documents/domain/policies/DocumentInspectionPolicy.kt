package dev.fajar.hris.documents.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*

fun validateDocumentInspection(
    revision: DocumentRevision,
    inspection: DocumentInspection,
): Result<Unit> =
    when {
        !inspection.clean -> Result.Failed(Failure(FailureKind.VALIDATION, "document_unsafe"))
        inspection.size != revision.size || inspection.sha256 != revision.sha256 ->
            Result.Failed(Failure(FailureKind.VALIDATION, "document_integrity_mismatch"))
        inspection.mediaType != revision.mediaType ->
            Result.Failed(Failure(FailureKind.VALIDATION, "document_type_mismatch"))
        else -> Result.Success(Unit)
    }

fun documentContentParts(
    revision: DocumentRevision,
    chunks: List<DocumentUploadChunk>,
): Result<List<DocumentContentPart>> {
    if (
        revision.uploadedBytes != revision.size ||
            chunks.size !=
                ((revision.size + DOCUMENT_CHUNK_BYTES - 1) / DOCUMENT_CHUNK_BYTES).toInt()
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "document_upload_incomplete"))
    val parts = ArrayList<DocumentContentPart>(chunks.size)
    var offset = 0L
    for ((index, chunk) in chunks.withIndex()) {
        val key = chunk.key
        val etag = chunk.etag
        if (
            !chunk.committed ||
                chunk.number != index + 1 ||
                chunk.offset != offset ||
                chunk.size.toLong() !=
                    minOf(DOCUMENT_CHUNK_BYTES.toLong(), revision.size - offset) ||
                chunk.attemptId == null ||
                key == null ||
                etag == null
        )
            return Result.Failed(Failure(FailureKind.CONFLICT, "document_upload_incomplete"))
        parts += DocumentContentPart(key, offset, chunk.size, chunk.sha256, etag)
        offset += chunk.size
    }
    return Result.Success(parts.toList())
}
