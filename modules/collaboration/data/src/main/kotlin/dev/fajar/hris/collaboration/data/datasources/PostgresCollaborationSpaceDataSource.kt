package dev.fajar.hris.collaboration.data.datasources

import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationSpaceDataSource(private val sql: DSLContext) :
    CollaborationSpaceDataSource {
    override fun current(): UUID? =
        sql.fetchOne("select space_id from public.collaboration_space_directory")
            ?.get("space_id", UUID::class.java)

    override fun contacts(
        spaceId: UUID,
        excludedAccountId: UUID,
        query: String,
        after: UUID?,
        accountLimit: Int,
        companyLimit: Int,
    ): List<CollaborationSpaceContactRow> {
        // Bind literal search text; '%' and '_' in a name are not wildcards.
        val pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
        return sql.fetch(
                """
                with contact_page as (
                    select account_id,display_name
                    from public.collaboration_space_contacts
                    where space_id=? and account_id<>? and (?::uuid is null or account_id>?)
                        and display_name ilike ? escape '!'
                    group by account_id,display_name
                    order by account_id limit ?
                )
                select company.space_id,contact.account_id,contact.display_name,
                    company.company_id,company.company_label
                from contact_page contact
                cross join lateral (
                    select space_id,company_id,company_label
                    from public.collaboration_space_contacts
                    where space_id=? and account_id=contact.account_id
                    order by company_id limit ?
                ) company
                order by contact.account_id,company.company_id
                """
                    .trimIndent(),
                spaceId,
                excludedAccountId,
                after,
                after,
                pattern,
                accountLimit,
                spaceId,
                companyLimit,
            )
            .map {
                CollaborationSpaceContactRow(
                    it.get("space_id", UUID::class.java),
                    it.get("account_id", UUID::class.java),
                    it.get("display_name", String::class.java),
                    it.get("company_id", UUID::class.java),
                    it.get("company_label", String::class.java),
                )
            }
    }
}
