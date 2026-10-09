package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.domain.entities.LeaveAttachment
import dev.fajar.hris.schema.tables.records.LeaveRequestAttachmentsRecord
import java.util.UUID

fun LeaveRequestAttachmentsRecord.toAttachment() =
    LeaveAttachment(
        documentId,
        documentRevisionId,
        fileName,
        mediaType,
        contentBytes,
        contentSha256,
    )

fun LeaveAttachment.toRow(company: UUID, request: UUID, position: Int) =
    LeaveRequestAttachmentsRecord().also {
        it.companyId = company
        it.requestId = request
        it.ordinal = position.toShort()
        it.documentId = documentId
        it.documentRevisionId = revisionId
        it.fileName = fileName
        it.mediaType = mediaType
        it.contentBytes = size
        it.contentSha256 = sha256
    }
