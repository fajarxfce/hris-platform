package dev.fajar.hris.identity.data.queries

import dev.fajar.hris.schema.Tables.IDENTITY_MAIL_DELIVERIES as D
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.Condition
import org.jooq.impl.DSL

fun identityMailFence(id: UUID, owner: UUID, token: UUID): Condition =
    D.CHALLENGE_ID.eq(id)
        .and(D.STATE.eq("LEASED"))
        .and(D.LEASE_OWNER.eq(owner))
        .and(D.LEASE_TOKEN.eq(token))
        .and(D.LEASE_UNTIL.gt(DSL.field("clock_timestamp()", OffsetDateTime::class.java)))
