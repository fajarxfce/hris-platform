package dev.fajar.hris.people.data.queries

import dev.fajar.hris.schema.tables.Companies.COMPANIES
import dev.fajar.hris.schema.tables.EmployeesAt
import dev.fajar.hris.schema.tables.EmployeesAt.EMPLOYEES_AT
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

fun employeesAtInstant(companyId: UUID, at: OffsetDateTime): EmployeesAt {
    val date =
        DSL.field(
            "({0} AT TIME ZONE {1})::date",
            SQLDataType.LOCALDATE,
            DSL.`val`(at, SQLDataType.TIMESTAMPWITHTIMEZONE),
            COMPANIES.TIMEZONE,
        )
    val companyDate = DSL.select(date).from(COMPANIES).where(COMPANIES.ID.eq(companyId))
    return EMPLOYEES_AT.call(DSL.`val`(companyId), companyDate.asField<LocalDate>())
}
