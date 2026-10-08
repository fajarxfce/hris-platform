package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.domain.entities.OidcIdentity
import dev.fajar.hris.schema.tables.records.OidcIdentitiesRecord
import java.time.ZoneOffset

fun OidcIdentitiesRecord.toIdentity() =
    OidcIdentity(id, accountId, issuer, subject, active, version, linkedAt.toInstant(), linkedBy)

fun OidcIdentity.toRow() =
    OidcIdentitiesRecord().also {
        it.id = id
        it.accountId = accountId
        it.issuer = issuer
        it.subject = subject
        it.active = active
        it.version = version
        it.linkedAt = linkedAt.atOffset(ZoneOffset.UTC)
        it.linkedBy = linkedBy
    }
