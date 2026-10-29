package dev.fajar.hris.administration.data.mappers

import dev.fajar.hris.administration.data.dto.PlatformAuditEventRow
import dev.fajar.hris.schema.tables.PlatformAuditMetadata.PLATFORM_AUDIT_METADATA as A
import org.jooq.Record

/** Convert an explicit metadata projection to a raw DTO; no reason/payload columns are selected. */
fun Record.toPlatformAuditEventRow() =
    PlatformAuditEventRow(
        requireNotNull(get(A.ID)),
        requireNotNull(get(A.ACTOR_ID)),
        requireNotNull(get(A.RESOURCE_TYPE)),
        requireNotNull(get(A.RESOURCE_ID)),
        requireNotNull(get(A.ACTION)),
        requireNotNull(get(A.CORRELATION_ID)),
        requireNotNull(get(A.CREATED_AT)),
    )
