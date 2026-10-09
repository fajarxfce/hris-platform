package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.schema.tables.LeaveRequestAttachments.LEAVE_REQUEST_ATTACHMENTS as A
import dev.fajar.hris.schema.tables.records.LeaveRequestAttachmentsRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresLeaveAttachmentDataSource(private val sql: DSLContext) : LeaveAttachmentDataSource {
    override fun list(companyId: UUID, requestId: UUID): List<LeaveRequestAttachmentsRecord> =
        sql.selectFrom(A)
            .where(A.COMPANY_ID.eq(companyId), A.REQUEST_ID.eq(requestId))
            .orderBy(A.ORDINAL)
            .limit(3)
            .fetch()

    override fun insert(rows: List<LeaveRequestAttachmentsRecord>) {
        if (rows.isNotEmpty()) sql.batch(rows.map { sql.insertInto(A).set(it) }).execute()
    }
}
