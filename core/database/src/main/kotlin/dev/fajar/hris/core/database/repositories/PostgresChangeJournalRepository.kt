package dev.fajar.hris.core.database.repositories

import dev.fajar.hris.core.database.datasources.ChangeJournalDataSource
import dev.fajar.hris.core.database.datasources.JournalRow
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.ChangeJournalRepository
import dev.fajar.hris.core.domain.ChangeRecord
import dev.fajar.hris.core.domain.Result
import java.util.UUID

class PostgresChangeJournalRepository(private val source: ChangeJournalDataSource) :
    ChangeJournalRepository {
    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> = safeDatabaseCall {
        source.append(
            JournalRow(
                UUID.randomUUID(),
                actor.companyId,
                actor.accountId,
                change.resourceType,
                change.resourceId,
                change.action,
                change.details,
                change.reason,
                actor.correlationId,
            )
        )
    }
}
