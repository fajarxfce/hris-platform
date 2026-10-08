package dev.fajar.hris.people.data.queries

import dev.fajar.hris.schema.Tables.EMPLOYMENT_REVISION_CANCELLATIONS as C
import dev.fajar.hris.schema.tables.EmploymentRevisions
import org.jooq.Condition
import org.jooq.impl.DSL

fun activeEmploymentRevision(revisions: EmploymentRevisions): Condition =
    DSL.notExists(
        DSL.selectOne()
            .from(C)
            .where(C.COMPANY_ID.eq(revisions.COMPANY_ID))
            .and(C.EMPLOYMENT_ID.eq(revisions.EMPLOYMENT_ID))
            .and(C.REVISION.eq(revisions.REVISION))
    )
