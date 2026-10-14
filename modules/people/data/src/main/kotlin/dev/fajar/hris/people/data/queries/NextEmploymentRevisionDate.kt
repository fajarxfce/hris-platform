package dev.fajar.hris.people.data.queries

import dev.fajar.hris.schema.tables.EmploymentRevisions.EMPLOYMENT_REVISIONS as H
import java.time.LocalDate
import java.util.UUID
import org.jooq.Field
import org.jooq.impl.DSL

fun nextEmploymentRevisionDate(
    company: Field<UUID>,
    employee: Field<UUID>,
    today: LocalDate,
): Field<LocalDate> =
    DSL.select(DSL.min(H.EFFECTIVE_FROM))
        .from(H)
        .where(H.COMPANY_ID.eq(company), H.EMPLOYMENT_ID.eq(employee), H.EFFECTIVE_FROM.gt(today))
        .and(activeEmploymentRevision(H))
        .asField()
