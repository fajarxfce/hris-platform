package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext

class PostgresReportExportExpiryDataSource(private val sql: DSLContext) :
    ReportExportExpiryDataSource {
    override fun due(at: Instant, limit: Int) =
        sql.resultQuery(
                """select company_id,account_id,export_id,expires_at,due_at from report_export_expiry_queue
        where due_at<=least(?::timestamptz,clock_timestamp()) order by due_at,company_id,account_id,export_id limit ?""",
                at.utc(),
                limit,
            )
            .fetch()
            .map {
                ReportExportExpiryRow(
                    it.get("company_id", UUID::class.java)!!,
                    it.get("account_id", UUID::class.java)!!,
                    it.get("export_id", UUID::class.java)!!,
                    it.get("expires_at", OffsetDateTime::class.java)!!.toInstant(),
                    it.get("due_at", OffsetDateTime::class.java)!!.toInstant(),
                )
            }

    override fun inspect(company: UUID, account: UUID, id: UUID, lock: Boolean) =
        sql.resultQuery(
                """select company_id,account_id,id,job_id,status,version,updated_at,expires_at,retired_at
        from report_exports where company_id=? and account_id=? and id=?""" +
                    (if (lock) " for update" else ""),
                company,
                account,
                id,
            )
            .fetchOne()
            ?.let {
                ReportExportRetirementRow(
                    it.get("company_id", UUID::class.java)!!,
                    it.get("account_id", UUID::class.java)!!,
                    it.get("id", UUID::class.java)!!,
                    it.get("job_id", UUID::class.java)!!,
                    it.get("status", String::class.java)!!,
                    it.get("version", Long::class.java)!!,
                    it.get("updated_at", OffsetDateTime::class.java)!!.toInstant(),
                    it.get("expires_at", OffsetDateTime::class.java)!!.toInstant(),
                    it.get("retired_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }

    override fun lockDue(company: UUID, account: UUID, id: UUID, at: Instant) =
        sql.resultQuery(
                """select 1 from report_export_expiry_queue
        where company_id=? and account_id=? and export_id=? and due_at<=least(?::timestamptz,clock_timestamp()) for update skip locked""",
                company,
                account,
                id,
                at.utc(),
            )
            .fetchOne() != null

    override fun stop(row: ReportExportRetirementRow, at: Instant) =
        sql.query(
                """update report_exports set status='STOPPED',failure_code='report_export_expired',finished_at=?::timestamptz,updated_at=?::timestamptz,version=version+1
        where company_id=? and account_id=? and id=? and job_id=? and version=? and status in ('QUEUED','RENDERING') and retired_at is null and expires_at<=least(?::timestamptz,clock_timestamp())""",
                at.utc(),
                at.utc(),
                row.company,
                row.account,
                row.id,
                row.job,
                row.version,
                at.utc(),
            )
            .execute()

    override fun retire(row: ReportExportRetirementRow, at: Instant) =
        sql.query(
                """update report_exports e set retired_at=?::timestamptz,updated_at=?::timestamptz,version=version+1
        where company_id=? and account_id=? and id=? and job_id=? and version=? and status in ('READY','STOPPED') and retired_at is null
        and expires_at<=least(?::timestamptz,clock_timestamp()) and (reservation_count=0 or expires_at+interval '1 hour'<=clock_timestamp())
        and not exists(select 1 from report_snapshot_rows r where r.company_id=e.company_id and r.account_id=e.account_id and r.snapshot_id=e.id)
        and not exists(select 1 from object_cleanup_queue q where q.company_id=e.company_id and q.resource_id=e.id)""",
                at.utc(),
                at.utc(),
                row.company,
                row.account,
                row.id,
                row.job,
                row.version,
                at.utc(),
            )
            .execute()

    override fun postpone(company: UUID, account: UUID, id: UUID, at: Instant) =
        sql.query(
                "update report_export_expiry_queue set due_at=?::timestamptz where company_id=? and account_id=? and export_id=?",
                at.utc(),
                company,
                account,
                id,
            )
            .execute()

    override fun retry(company: UUID, account: UUID, id: UUID, observed: Instant, at: Instant) =
        sql.query(
                "update report_export_expiry_queue set due_at=?::timestamptz where company_id=? and account_id=? and export_id=? and due_at=?::timestamptz",
                at.utc(),
                company,
                account,
                id,
                observed.utc(),
            )
            .execute()

    override fun remove(company: UUID, account: UUID, id: UUID) =
        sql.query(
                "delete from report_export_expiry_queue where company_id=? and account_id=? and export_id=?",
                company,
                account,
                id,
            )
            .execute()

    private fun Instant.utc() = atOffset(ZoneOffset.UTC)
}
