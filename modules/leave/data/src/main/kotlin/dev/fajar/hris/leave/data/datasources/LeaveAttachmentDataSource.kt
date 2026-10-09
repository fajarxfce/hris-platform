package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.schema.tables.records.LeaveRequestAttachmentsRecord
import java.util.UUID

interface LeaveAttachmentDataSource {
    fun list(companyId: UUID, requestId: UUID): List<LeaveRequestAttachmentsRecord>

    fun insert(rows: List<LeaveRequestAttachmentsRecord>)
}
