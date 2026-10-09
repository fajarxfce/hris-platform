package dev.fajar.hris.sync.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.domain.entities.SyncMaintenanceResult
import dev.fajar.hris.sync.domain.repositories.SyncRepository
import java.time.Clock
import java.util.UUID

/** Queue maintenance requires the worker database capability, never a caller's company grants. */
class MaintainMobileSync(
    private val sync: SyncRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(): Result<SyncMaintenanceResult> {
        val worker = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())
        return transactions
            .run(worker) { sync.publish(200) }
            .flatMap { published ->
                transactions
                    .run(worker) { sync.prune(500) }
                    .map { pruned -> SyncMaintenanceResult(published, pruned) }
            }
    }
}
