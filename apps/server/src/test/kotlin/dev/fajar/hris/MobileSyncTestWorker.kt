package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.data.datasources.*
import dev.fajar.hris.sync.data.repositories.StoredSyncRepository
import dev.fajar.hris.sync.domain.repositories.SyncRepository
import dev.fajar.hris.sync.domain.usecases.MaintainMobileSync
import java.time.Clock
import java.util.UUID
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager

const val MOBILE_SYNC_TEST_KEYS = "HRIS_SYNC_KEYS=v1:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="

class MobileSyncTestWorker(val source: SyncDataSource, val transactions: TransactionRunner) {
    val repository: SyncRepository = StoredSyncRepository(source)
    val maintain = MaintainMobileSync(repository, transactions, Clock.systemUTC())

    fun publish(limit: Int = 200): Result<Int> =
        transactions.run(actor()) { repository.publish(limit) }

    fun prune(limit: Int = 500): Result<Int> = transactions.run(actor()) { repository.prune(limit) }

    fun actor() =
        Actor(UUID(0, 0), null, emptySet(), Clock.systemUTC().instant(), UUID.randomUUID())

    fun decorated(transform: (SyncDataSource) -> SyncDataSource) =
        MobileSyncTestWorker(transform(source), transactions)
}

/** This role can maintain synchronization metadata, without bypassing business RLS. */
fun mobileSyncTestWorker(jdbcUrl: String, database: JdbcTemplate): MobileSyncTestWorker {
    database.execute(
        """do ${'$'}${'$'} begin if not exists(select 1 from pg_roles where rolname='mobile_sync_test') then create role mobile_sync_test login password 'sync-fixture-only' nosuperuser nobypassrls;end if;end ${'$'}${'$'}"""
    )
    database.execute("grant hris_worker_capability to mobile_sync_test")
    database.execute("grant usage on schema public to mobile_sync_test")
    val source = DriverManagerDataSource(jdbcUrl, "mobile_sync_test", "sync-fixture-only")
    val sql =
        DSL.using(
            TransactionAwareDataSourceProxy(source),
            SQLDialect.POSTGRES,
            Settings().withExecuteLogging(false),
        )
    return MobileSyncTestWorker(
        PostgresSyncDataSource(sql),
        PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source)),
    )
}
