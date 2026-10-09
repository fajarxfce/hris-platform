package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource
import dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository
import dev.fajar.hris.storage.data.datasources.*
import dev.fajar.hris.storage.data.repositories.*
import dev.fajar.hris.storage.domain.usecases.CollectObjectGarbage
import java.time.Clock
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import tools.jackson.databind.ObjectMapper

/** A separate non-bypass worker role in the owned test database. */
fun documentCleanupWorker(
    jdbcUrl: String,
    database: JdbcTemplate,
    storage: ObjectStorageDataSource,
    json: ObjectMapper,
): CollectObjectGarbage {
    database.execute(
        """do ${'$'}${'$'} begin if not exists(select 1 from pg_roles where rolname='document_cleanup_test') then create role document_cleanup_test login password 'document-fixture-only' nosuperuser nobypassrls;end if;end ${'$'}${'$'}"""
    )
    database.execute("grant hris_worker_capability to document_cleanup_test")
    database.execute("grant usage on schema public to document_cleanup_test")
    database.execute(
        "grant select,insert,update,delete on all tables in schema public to document_cleanup_test"
    )
    val source = DriverManagerDataSource(jdbcUrl, "document_cleanup_test", "document-fixture-only")
    val sql =
        DSL.using(
            TransactionAwareDataSourceProxy(source),
            SQLDialect.POSTGRES,
            Settings().withExecuteLogging(false),
        )
    return CollectObjectGarbage(
        PostgresObjectCleanupRepository(PostgresObjectCleanupDataSource(sql)),
        PrivateObjectStorageRepository(storage),
        PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json)),
        PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source)),
        Clock.systemUTC(),
    )
}
