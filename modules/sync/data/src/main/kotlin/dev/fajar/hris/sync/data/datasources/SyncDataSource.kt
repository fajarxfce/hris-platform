package dev.fajar.hris.sync.data.datasources

import dev.fajar.hris.schema.tables.records.MobileSyncChangesRecord
import dev.fajar.hris.schema.tables.records.MobileSyncHeadsRecord
import dev.fajar.hris.sync.data.models.*
import java.util.UUID

interface SyncDataSource {
    fun head(companyId: UUID): MobileSyncHeadsRecord

    fun snapshot(
        selection: SyncSelection,
        afterCollection: String?,
        afterId: UUID?,
        limit: Int,
    ): List<SyncResourceRow>

    fun changes(
        selection: SyncSelection,
        after: Long,
        upper: Long,
        limit: Int,
    ): List<MobileSyncChangesRecord>

    fun pending(selection: SyncSelection): Boolean

    fun publish(limit: Int): Int

    fun prune(limit: Int): Int
}
