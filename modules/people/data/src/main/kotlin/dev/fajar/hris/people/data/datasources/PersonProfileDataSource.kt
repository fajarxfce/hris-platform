package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.PersonProfileRevisionsRecord
import dev.fajar.hris.schema.tables.records.PersonsRecord
import java.util.UUID

interface PersonProfileDataSource {
    fun insertAccountLink(row: dev.fajar.hris.schema.tables.records.PersonAccountLinksRecord)

    fun bindAccount(
        companyId: UUID,
        personId: UUID,
        accountId: UUID,
        expectedVersion: Long,
    ): PersonsRecord?

    fun findForEmployee(companyId: UUID, employeeId: UUID): PersonsRecord?

    fun update(row: PersonsRecord, expectedVersion: Long): PersonsRecord?

    fun insertRevision(row: PersonProfileRevisionsRecord)

    fun history(personId: UUID, after: Long?, limit: Int): List<PersonProfileRevisionsRecord>
}
