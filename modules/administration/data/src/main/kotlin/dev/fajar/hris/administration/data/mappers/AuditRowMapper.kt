package dev.fajar.hris.administration.data.mappers

import dev.fajar.hris.administration.data.dto.AuditEventRow
import dev.fajar.hris.schema.tables.AuditEntries.AUDIT_ENTRIES as A
import org.jooq.Record

/** Convert an explicit metadata projection to a raw DTO; no reason/payload columns are selected. */
fun Record.toAuditEventRow() =
    AuditEventRow(
        requireNotNull(get(A.ID)),
        requireNotNull(get(A.COMPANY_ID)),
        requireNotNull(get(A.ACTOR_ID)),
        requireNotNull(get(A.RESOURCE_TYPE)),
        requireNotNull(get(A.RESOURCE_ID)),
        requireNotNull(get(A.ACTION)),
        requireNotNull(get(A.CORRELATION_ID)),
        requireNotNull(get(A.CREATED_AT)),
    )
