package dev.fajar.hris.worker.di

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import dev.fajar.hris.jobs.domain.usecases.*
import dev.fajar.hris.worker.runtime.*
import dev.fajar.hris.worker.tasks.WorkPeriodCloseTask
import dev.fajar.hris.workforce.domain.usecases.*
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
class WorkerConfiguration {
    @Bean
    fun workPeriodCloseTask(
        resolve: ResolveActor,
        advance: AdvanceWorkPeriodClose,
        abort: AbortWorkPeriodClose,
    ): JobTask = WorkPeriodCloseTask(resolve, advance, abort)

    @Bean fun workerSettings() = WorkerSettings()

    @Bean
    @Primary
    fun workerTransactions(
        manager: PlatformTransactionManager,
        jdbc: JdbcTemplate,
    ): TransactionRunner =
        PostgresTransactionRunner(
            manager,
            jdbc,
            java.time.Duration.ofSeconds(10),
            java.time.Duration.ofSeconds(2),
        )

    @Bean
    fun batchExecutor(
        operator: JobOperator,
        repository: JobRepository,
        manager: PlatformTransactionManager,
        settings: WorkerSettings,
    ) = BatchJobExecutor(operator, repository, manager, settings)

    @Bean
    fun jobRunExecutor(batch: BatchJobExecutor, defer: DeferJob) = JobRunExecutor(batch, defer)

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun scheduler(
        tasks: List<JobTask>,
        lease: LeaseJobs,
        keepAlive: KeepJobAlive,
        defer: DeferJob,
        run: JobRunExecutor,
        settings: WorkerSettings,
    ) = JobScheduler(tasks, lease, keepAlive, defer, run, settings)
}
