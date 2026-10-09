package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import java.time.Duration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

@TestConfiguration(proxyBeanMethods = false)
class PayrollPublicationBudgetConfiguration {
    @Bean
    @Primary
    fun publicationTransactions(manager: PlatformTransactionManager, jdbc: JdbcTemplate) =
        PostgresTransactionRunner(manager, jdbc, Duration.ofSeconds(10), Duration.ofSeconds(2))
}
