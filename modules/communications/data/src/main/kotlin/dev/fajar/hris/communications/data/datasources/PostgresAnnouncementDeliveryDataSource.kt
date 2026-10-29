package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.AnnouncementDeliveryRow
import java.util.UUID
import org.jooq.DSLContext

class PostgresAnnouncementDeliveryDataSource(private val sql: DSLContext) :
    AnnouncementDeliveryDataSource {
    override fun counts(companyId: UUID, announcementId: UUID): AnnouncementDeliveryRow {
        val row =
            sql.fetchOne(
                """WITH candidates AS MATERIALIZED (
            SELECT state,processed_count,accepted_count,rejected_count FROM announcement_push_delivery
            WHERE company_id=? AND announcement_id=? ORDER BY inbox_id LIMIT 5001
        ) SELECT count(*) FILTER (WHERE state='PENDING') AS pending,
            count(*) FILTER (WHERE state='LEASED') AS processing,
            count(*) FILTER (WHERE state='COMPLETE') AS completed,
            count(*) FILTER (WHERE state='FAILED') AS failed,
            count(*) FILTER (WHERE state='SUPERSEDED') AS superseded,
            coalesce(sum(processed_count),0) AS processed, coalesce(sum(accepted_count),0) AS accepted,
            coalesce(sum(rejected_count),0) AS rejected FROM candidates""",
                companyId,
                announcementId,
            )!!
        return AnnouncementDeliveryRow(
            row.get("pending", Int::class.java)!!,
            row.get("processing", Int::class.java)!!,
            row.get("completed", Int::class.java)!!,
            row.get("failed", Int::class.java)!!,
            row.get("superseded", Int::class.java)!!,
            row.get("processed", Int::class.java)!!,
            row.get("accepted", Int::class.java)!!,
            row.get("rejected", Int::class.java)!!,
        )
    }
}
