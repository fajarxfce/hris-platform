package dev.fajar.hris.leave.data.queries

import dev.fajar.hris.leave.data.models.LeaveTypeRow
import dev.fajar.hris.schema.tables.LeaveTypeRevisions.LEAVE_TYPE_REVISIONS as R
import dev.fajar.hris.schema.tables.LeaveTypes.LEAVE_TYPES as T
import org.jooq.*

fun selectLeaveTypes(sql: DSLContext) =
    sql.select(T.ID, T.CODE, T.VERSION, R.REVISION, R.EFFECTIVE_FROM, R.DETAILS, R.ACTIVE)
        .distinctOn(T.CODE)
        .from(T)
        .join(R)
        .on(R.COMPANY_ID.eq(T.COMPANY_ID).and(R.TYPE_ID.eq(T.ID)))

fun Record.toLeaveTypeRow(): LeaveTypeRow =
    LeaveTypeRow(
        requireNotNull(get(T.ID)),
        requireNotNull(get(T.CODE)),
        requireNotNull(get(T.VERSION)),
        requireNotNull(get(R.REVISION)),
        requireNotNull(get(R.EFFECTIVE_FROM)),
        requireNotNull(get(R.DETAILS)),
        requireNotNull(get(R.ACTIVE)),
    )
