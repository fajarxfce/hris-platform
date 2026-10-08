package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.Tables.*
import dev.fajar.hris.schema.tables.records.PersonProfileRevisionsRecord
import dev.fajar.hris.schema.tables.records.PersonsRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresPersonProfileDataSource(private val sql: DSLContext) : PersonProfileDataSource {
    override fun findForEmployee(companyId: UUID, employeeId: UUID): PersonsRecord? =
        sql.select(PERSONS.fields().toList())
            .from(PERSONS)
            .join(EMPLOYMENTS)
            .on(EMPLOYMENTS.PERSON_ID.eq(PERSONS.ID))
            .where(EMPLOYMENTS.COMPANY_ID.eq(companyId).and(EMPLOYMENTS.ID.eq(employeeId)))
            .fetchOneInto(PERSONS)

    override fun update(row: PersonsRecord, expectedVersion: Long): PersonsRecord? =
        sql.update(PERSONS)
            .set(PERSONS.LEGAL_NAME, row.legalName)
            .set(PERSONS.BIRTH_DATE, row.birthDate)
            .set(PERSONS.NATIONALITY, row.nationality)
            .set(PERSONS.EMAIL, row.email)
            .set(PERSONS.VERSION, PERSONS.VERSION.plus(1))
            .where(
                PERSONS.ID.eq(row.id)
                    .and(PERSONS.OWNER_COMPANY_ID.eq(row.ownerCompanyId))
                    .and(PERSONS.VERSION.eq(expectedVersion))
            )
            .returning()
            .fetchOne()

    override fun insertRevision(row: PersonProfileRevisionsRecord) {
        sql.insertInto(PERSON_PROFILE_REVISIONS).set(row).execute()
    }

    override fun history(
        personId: UUID,
        after: Long?,
        limit: Int,
    ): List<PersonProfileRevisionsRecord> =
        sql.selectFrom(PERSON_PROFILE_REVISIONS)
            .where(
                PERSON_PROFILE_REVISIONS.PERSON_ID.eq(personId)
                    .and(
                        after?.let { PERSON_PROFILE_REVISIONS.REVISION.gt(it) } ?: DSL.noCondition()
                    )
            )
            .orderBy(PERSON_PROFILE_REVISIONS.REVISION)
            .limit(limit)
            .fetch()
}
