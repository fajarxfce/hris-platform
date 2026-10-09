package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.schema.Tables.ANNOUNCEMENT_PUBLICATIONS as P
import dev.fajar.hris.schema.tables.records.AnnouncementPublicationsRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresAnnouncementPublicationDataSource(private val sql: DSLContext) :
    AnnouncementPublicationDataSource {
    override fun insertPublication(row: AnnouncementPublicationsRecord) {
        sql.insertInto(P).set(row).execute()
    }

    override fun insertInbox(companyId: UUID, publicationId: UUID): Int =
        sql.execute(
            """
        INSERT INTO inbox_items(company_id,publication_id,announcement_id,owner_account_id,employment_id,acknowledgement_required,delivered_at)
        SELECT p.company_id,p.id,p.announcement_id,target.key::uuid,target.value::uuid,r.acknowledgement_required,p.published_at
        FROM announcement_publications p JOIN announcement_revisions r
            ON r.company_id=p.company_id AND r.id=p.announcement_id AND r.version=p.content_version
        CROSS JOIN LATERAL jsonb_each_text(p.recipients) target
        WHERE p.company_id=? AND p.id=?
    """,
            companyId,
            publicationId,
        )
}
