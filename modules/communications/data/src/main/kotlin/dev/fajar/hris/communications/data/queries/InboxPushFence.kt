package dev.fajar.hris.communications.data.queries

import dev.fajar.hris.schema.tables.InboxPushDispatches.INBOX_PUSH_DISPATCHES as D
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Condition
import org.jooq.impl.DSL

fun inboxPushFence(companyId: UUID, inboxId: UUID, owner: UUID, token: UUID): Condition =
    D.COMPANY_ID.eq(companyId)
        .and(D.INBOX_ID.eq(inboxId))
        .and(D.STATE.eq("LEASED"))
        .and(D.LEASE_OWNER.eq(owner))
        .and(D.LEASE_TOKEN.eq(token))
        .and(D.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
