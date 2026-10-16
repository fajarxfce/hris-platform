package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.identity.data.models.NativeSessionCleanupData
import java.time.Instant

fun interface NativeSessionCleanupDataSource {
    fun prune(
        expiredBefore: Instant,
        replayBefore: Instant,
        sessionLimit: Int,
        replayLimit: Int,
    ): NativeSessionCleanupData
}
