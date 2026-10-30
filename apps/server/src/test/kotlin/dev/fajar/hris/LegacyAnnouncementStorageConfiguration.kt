package dev.fajar.hris

import dev.fajar.hris.communications.data.datasources.AnnouncementDataSource
import dev.fajar.hris.communications.data.datasources.PostgresAnnouncementDataSource
import dev.fajar.hris.schema.tables.AnnouncementHeads.ANNOUNCEMENT_HEADS as H
import dev.fajar.hris.schema.tables.AnnouncementRevisions.ANNOUNCEMENT_REVISIONS as R
import dev.fajar.hris.schema.tables.records.AnnouncementRevisionsRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** The V101 acceptance uses the actual schema 100 and its pre-email writer contract. */
@TestConfiguration(proxyBeanMethods = false)
class LegacyAnnouncementStorageConfiguration {
    @Bean
    @Primary
    fun schema100Announcements(sql: DSLContext): AnnouncementDataSource =
        LegacyAnnouncementDataSource(sql)
}

private class LegacyAnnouncementDataSource(private val sql: DSLContext) :
    AnnouncementDataSource by PostgresAnnouncementDataSource(sql) {
    private val fields = R.fields().filter { it != R.EMAIL_NOTIFICATION }
    private val projection = fields + DSL.inline(false).`as`(R.EMAIL_NOTIFICATION)

    override fun find(companyId: UUID, id: UUID, revision: Long?): AnnouncementRevisionsRecord? =
        sql.select(projection)
            .from(R)
            .where(R.COMPANY_ID.eq(companyId), R.ID.eq(id))
            .and(
                R.VERSION.eq(
                    revision?.let { DSL.inline(it) }
                        ?: sql.select(H.VERSION)
                            .from(H)
                            .where(H.COMPANY_ID.eq(companyId), H.ID.eq(id))
                            .asField()
                )
            )
            .fetchOneInto(R)

    override fun forJob(companyId: UUID, jobId: UUID): AnnouncementRevisionsRecord? =
        sql.select(projection)
            .from(R)
            .join(H)
            .on(H.COMPANY_ID.eq(R.COMPANY_ID), H.ID.eq(R.ID), H.VERSION.eq(R.VERSION))
            .where(R.COMPANY_ID.eq(companyId), R.PUBLICATION_JOB_ID.eq(jobId))
            .fetchOneInto(R)

    override fun insertRevision(row: AnnouncementRevisionsRecord) {
        check(row.emailNotification == false) { "Schema 100 has no email channel" }
        sql.insertInto(R)
            .set(fields.filter { row.touched(it) }.associateWith { row.get(it) })
            .execute()
    }
}
