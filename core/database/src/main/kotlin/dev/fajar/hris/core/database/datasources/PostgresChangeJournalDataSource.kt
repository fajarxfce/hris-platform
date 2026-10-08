package dev.fajar.hris.core.database.datasources

import dev.fajar.hris.schema.tables.AuditEntries.AUDIT_ENTRIES
import dev.fajar.hris.schema.tables.OutboxEvents.OUTBOX_EVENTS
import org.jooq.DSLContext
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class PostgresChangeJournalDataSource(private val sql: DSLContext, private val json: ObjectMapper) :
    ChangeJournalDataSource {
    override fun append(row: JournalRow) {
        val payload = JSONB.valueOf(json.writeValueAsString(row.details))
        sql.insertInto(AUDIT_ENTRIES)
            .set(AUDIT_ENTRIES.ID, row.id)
            .set(AUDIT_ENTRIES.COMPANY_ID, row.companyId)
            .set(AUDIT_ENTRIES.ACTOR_ID, row.actorId)
            .set(AUDIT_ENTRIES.RESOURCE_TYPE, row.resourceType)
            .set(AUDIT_ENTRIES.RESOURCE_ID, row.resourceId)
            .set(AUDIT_ENTRIES.ACTION, row.action)
            .set(AUDIT_ENTRIES.REASON, row.reason)
            .set(AUDIT_ENTRIES.DETAILS, payload)
            .set(AUDIT_ENTRIES.CORRELATION_ID, row.correlationId)
            .execute()
        sql.insertInto(OUTBOX_EVENTS)
            .set(OUTBOX_EVENTS.ID, row.id)
            .set(OUTBOX_EVENTS.COMPANY_ID, row.companyId)
            .set(OUTBOX_EVENTS.ACTOR_ID, row.actorId)
            .set(OUTBOX_EVENTS.EVENT_TYPE, row.action)
            .set(OUTBOX_EVENTS.RESOURCE_ID, row.resourceId)
            .set(OUTBOX_EVENTS.PAYLOAD, payload)
            .execute()
    }
}
