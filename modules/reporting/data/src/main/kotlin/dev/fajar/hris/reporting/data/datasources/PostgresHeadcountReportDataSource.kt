package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.HeadcountAggregateRow
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresHeadcountReportDataSource(private val sql: DSLContext) : HeadcountReportDataSource {
    override fun count(
        companies: Set<UUID>,
        asOf: LocalDate,
        statuses: Set<String>,
    ): List<HeadcountAggregateRow> =
        sql.fetch(
                """
                with effective as materialized (
                    select e.company_id, e.person_id, r.status, r.contract_kind
                    from employments e
                    join lateral (
                        select h.status, h.contract_kind, h.start_date, h.end_date
                        from employment_revisions h
                        where h.company_id=e.company_id and h.employment_id=e.id
                          and h.effective_from<=?::date
                          and not exists (
                              select 1 from employment_revision_cancellations c
                              where c.company_id=h.company_id and c.employment_id=h.employment_id
                                and c.revision=h.revision
                          )
                        order by h.effective_from desc,h.revision desc limit 1
                    ) r on true
                    where e.company_id=any(?::uuid[]) and r.status=any(?::varchar[])
                      and r.start_date<=?::date and (r.end_date is null or r.end_date>=?::date)
                )
                select company_id, case when grouping(status)=0 then 'STATUS'
                            when grouping(contract_kind)=0 then 'CONTRACT' else 'TOTAL' end as dimension,
                       coalesce(status,contract_kind) as key,
                       count(*) as employments,count(distinct person_id) as persons
                from effective group by grouping sets (
                    (),(status),(contract_kind),
                    (company_id),(company_id,status),(company_id,contract_kind)
                )
                """
                    .trimIndent(),
                asOf,
                companies.toTypedArray(),
                statuses.toTypedArray(),
                asOf,
                asOf,
            )
            .map {
                HeadcountAggregateRow(
                    requireNotNull(it.get("dimension", String::class.java)),
                    it.get("key", String::class.java),
                    requireNotNull(it.get("employments", Long::class.java)),
                    requireNotNull(it.get("persons", Long::class.java)),
                    it.get("company_id", UUID::class.java),
                )
            }
}
