package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.schema.tables.records.PersonProfileRevisionsRecord
import dev.fajar.hris.schema.tables.records.PersonsRecord
import java.util.UUID

fun PersonsRecord.toManagedProfile() =
    ManagedPersonProfile(
        PersonProfile(id, accountId, legalName, birthDate, nationality, email),
        ownerCompanyId,
        version,
    )

fun PersonProfile.toProfileRevision(companyId: UUID, version: Long, actorId: UUID, reason: String) =
    PersonProfileRevisionsRecord().also {
        it.personId = id
        it.revision = version
        it.ownerCompanyId = companyId
        it.legalName = legalName
        it.birthDate = birthDate
        it.nationality = nationality
        it.email = email
        it.actorId = actorId
        it.reason = reason
    }

fun PersonProfileRevisionsRecord.toProfileRevision() =
    PersonProfileRevision(
        revision,
        PersonProfile(personId, null, legalName, birthDate, nationality, email),
        actorId,
        reason,
        recordedAt.toInstant(),
    )
