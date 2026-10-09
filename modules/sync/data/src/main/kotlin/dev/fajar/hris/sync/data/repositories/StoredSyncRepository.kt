package dev.fajar.hris.sync.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.data.datasources.SyncDataSource
import dev.fajar.hris.sync.data.mappers.*
import dev.fajar.hris.sync.domain.entities.*
import dev.fajar.hris.sync.domain.repositories.SyncRepository
import java.util.UUID

class StoredSyncRepository(private val source: SyncDataSource) : SyncRepository {
    override fun head(companyId: UUID): Result<SyncHead> = safeDatabaseCall {
        source.head(companyId).let {
            SyncHead(it.epoch, it.headPosition, it.prunedThrough, it.publishedAt?.toInstant())
        }
    }

    override fun snapshot(
        scope: SyncScope,
        after: SyncResourceKey?,
        limit: Int,
    ): Result<List<SyncResource>> = safeDatabaseCall {
        source.snapshot(scope.toSelection(), after?.collection?.name, after?.id, limit).map {
            it.toResource()
        }
    }

    override fun changes(
        scope: SyncScope,
        after: Long,
        upper: Long,
        limit: Int,
    ): Result<List<SyncChange>> = safeDatabaseCall {
        source.changes(scope.toSelection(), after, upper, limit).map {
            SyncChange(
                requireNotNull(it.sequence),
                SyncResource(
                    SyncResourceKey(SyncCollection.valueOf(it.collection), it.resourceId),
                    it.resourceVersion,
                ),
                SyncOperation.valueOf(it.operation),
            )
        }
    }

    override fun pending(scope: SyncScope): Result<Boolean> = safeDatabaseCall {
        source.pending(scope.toSelection())
    }

    override fun publish(limit: Int): Result<Int> = safeDatabaseCall { source.publish(limit) }

    override fun prune(limit: Int): Result<Int> = safeDatabaseCall { source.prune(limit) }
}
