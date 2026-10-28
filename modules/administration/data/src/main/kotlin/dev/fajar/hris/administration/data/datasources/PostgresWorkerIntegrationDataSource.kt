package dev.fajar.hris.administration.data.datasources

import dev.fajar.hris.administration.data.dto.WorkerIntegrationRow
import dev.fajar.hris.administration.data.dto.WorkerIntegrationWriteRow
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresWorkerIntegrationDataSource(private val sql: DSLContext) :
    WorkerIntegrationDataSource {
    override fun lockRegistry() {
        sql.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended('hris.worker-integration-registry',0))"
        )
    }

    override fun read(limit: Int): List<WorkerIntegrationRow> =
        sql.fetch(
                """
                SELECT statement_timestamp() AS observed_at,r.instance_id,r.first_seen_at,r.last_seen_at,
                       r.mail_enabled,r.push_enabled,r.storage_enabled,r.scanner_enabled
                FROM (SELECT 1) singleton LEFT JOIN worker_integration_observations r ON true
                ORDER BY r.instance_id LIMIT ?
                """
                    .trimIndent(),
                limit,
            )
            .map { row ->
                WorkerIntegrationRow(
                    requireNotNull(row.get("observed_at", OffsetDateTime::class.java)),
                    row.get("instance_id", UUID::class.java),
                    row.get("first_seen_at", OffsetDateTime::class.java),
                    row.get("last_seen_at", OffsetDateTime::class.java),
                    row.get("mail_enabled", Boolean::class.javaObjectType),
                    row.get("push_enabled", Boolean::class.javaObjectType),
                    row.get("storage_enabled", Boolean::class.javaObjectType),
                    row.get("scanner_enabled", Boolean::class.javaObjectType),
                )
            }

    override fun expire(before: OffsetDateTime) =
        sql.execute(
            "DELETE FROM worker_integration_observations WHERE last_seen_at<?::timestamptz",
            before,
        )

    override fun publish(row: WorkerIntegrationWriteRow) =
        sql.execute(
            """
            WITH moment AS(SELECT clock_timestamp() AS at)
            INSERT INTO worker_integration_observations
                (instance_id,first_seen_at,last_seen_at,mail_enabled,push_enabled,storage_enabled,scanner_enabled)
            SELECT ?::uuid,at,at,?::boolean,?::boolean,?::boolean,?::boolean FROM moment
            ON CONFLICT(instance_id) DO UPDATE
            SET last_seen_at=greatest(worker_integration_observations.last_seen_at,EXCLUDED.last_seen_at)
            WHERE (worker_integration_observations.mail_enabled,worker_integration_observations.push_enabled,
                   worker_integration_observations.storage_enabled,worker_integration_observations.scanner_enabled)
                =(EXCLUDED.mail_enabled,EXCLUDED.push_enabled,EXCLUDED.storage_enabled,EXCLUDED.scanner_enabled)
            """
                .trimIndent(),
            row.instanceId,
            row.mail,
            row.push,
            row.storage,
            row.scanner,
        )
}
