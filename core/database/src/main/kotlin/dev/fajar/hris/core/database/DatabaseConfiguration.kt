package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.TransactionRunner
import javax.sql.DataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
class DatabaseConfiguration {
    @Bean
    fun operationReceipts(
        sql: org.jooq.DSLContext
    ): dev.fajar.hris.core.database.datasources.OperationReceiptDataSource =
        dev.fajar.hris.core.database.datasources.PostgresOperationReceiptDataSource(sql)

    @Bean
    fun changeJournal(
        source: dev.fajar.hris.core.database.datasources.ChangeJournalDataSource
    ): dev.fajar.hris.core.domain.ChangeJournalRepository =
        dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository(source)

    @Bean
    fun journalSource(
        sql: org.jooq.DSLContext,
        json: tools.jackson.databind.ObjectMapper,
    ): dev.fajar.hris.core.database.datasources.ChangeJournalDataSource =
        dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource(sql, json)

    @Bean
    fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        JdbcTransactionManager(dataSource)

    @Bean
    fun transactionRunner(
        manager: PlatformTransactionManager,
        jdbc: JdbcTemplate,
    ): TransactionRunner = PostgresTransactionRunner(manager, jdbc)
}
