package dev.fajar.hris.administration.data.repositories

import dev.fajar.hris.administration.data.datasources.PlatformAuditDataSource
import dev.fajar.hris.administration.data.dto.AuditQueryRow
import dev.fajar.hris.administration.data.mappers.toPlatformAuditEvent
import dev.fajar.hris.administration.domain.entities.AuditQuery
import dev.fajar.hris.administration.domain.repositories.PlatformAuditRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Page
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID

class StoredPlatformAuditRepository(private val source: PlatformAuditDataSource) :
    PlatformAuditRepository {
    override fun find(id: UUID) = safeDatabaseCall { source.find(id)?.toPlatformAuditEvent() }

    override fun search(query: AuditQuery) = safeDatabaseCall {
        check(query.limit in 1..200)
        val rows =
            source.search(
                AuditQueryRow(
                    query.from.atOffset(ZoneOffset.UTC),
                    query.until.atOffset(ZoneOffset.UTC),
                    query.actorId,
                    query.resourceType,
                    query.resourceId,
                    query.action,
                    query.before?.recordedAt?.atOffset(ZoneOffset.UTC),
                    query.before?.id,
                    query.limit + 1,
                )
            )
        check(rows.size <= query.limit + 1)
        val values = rows.map { it.toPlatformAuditEvent() }
        Page(
            Collections.unmodifiableList(values.take(query.limit)),
            if (values.size > query.limit) values[query.limit - 1].id.toString() else null,
        )
    }
}
