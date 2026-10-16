package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.identity.data.models.NativeSessionCleanupData
import java.time.Instant
import java.time.ZoneOffset
import org.jooq.DSLContext

class PostgresNativeSessionCleanupDataSource(private val sql: DSLContext) :
    NativeSessionCleanupDataSource {
    override fun prune(
        expiredBefore: Instant,
        replayBefore: Instant,
        sessionLimit: Int,
        replayLimit: Int,
    ): NativeSessionCleanupData {
        val result =
            requireNotNull(
                sql.fetchOne(
                    "select * from prune_native_session_material(?::timestamptz,?::timestamptz,?::integer,?::integer)",
                    expiredBefore.atOffset(ZoneOffset.UTC),
                    replayBefore.atOffset(ZoneOffset.UTC),
                    sessionLimit,
                    replayLimit,
                )
            )
        return NativeSessionCleanupData(
            requireNotNull(result.get("deleted_sessions", Int::class.java)),
            requireNotNull(result.get("cleared_exchange_replays", Int::class.java)),
            requireNotNull(result.get("cleared_refresh_replays", Int::class.java)),
        )
    }
}
