package dev.fajar.hris.people.data.queries

import dev.fajar.hris.schema.Tables.EMPLOYMENT_REVISIONS
import dev.fajar.hris.schema.tables.EmploymentRevisions
import org.jooq.Condition
import org.jooq.impl.DSL

fun latestEmploymentRevision(revisions: EmploymentRevisions): Condition {
    val latest = EMPLOYMENT_REVISIONS.`as`("latest_revision")
    return activeEmploymentRevision(revisions)
        .and(
            revisions.REVISION.eq(
                DSL.select(DSL.max(latest.REVISION))
                    .from(latest)
                    .where(latest.COMPANY_ID.eq(revisions.COMPANY_ID))
                    .and(latest.EMPLOYMENT_ID.eq(revisions.EMPLOYMENT_ID))
                    .and(latest.EFFECTIVE_FROM.eq(revisions.EFFECTIVE_FROM))
                    .and(activeEmploymentRevision(latest))
            )
        )
}
