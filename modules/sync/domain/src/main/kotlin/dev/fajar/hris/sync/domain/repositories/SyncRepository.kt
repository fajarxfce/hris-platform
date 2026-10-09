package dev.fajar.hris.sync.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.domain.entities.*
import java.util.UUID

interface SyncRepository {
    /**
     * A shared read guard freezes publication and retention positions until the transaction ends.
     */
    fun head(companyId: UUID): Result<SyncHead>

    fun snapshot(scope: SyncScope, after: SyncResourceKey?, limit: Int): Result<List<SyncResource>>

    fun changes(scope: SyncScope, after: Long, upper: Long, limit: Int): Result<List<SyncChange>>

    fun pending(scope: SyncScope): Result<Boolean>

    fun publish(limit: Int): Result<Int>

    fun prune(limit: Int): Result<Int>
}
