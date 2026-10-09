package dev.fajar.hris.worker.di

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import dev.fajar.hris.jobs.domain.usecases.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.usecases.*
import dev.fajar.hris.worker.runtime.*
import dev.fajar.hris.worker.tasks.*
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
    ): PostgresTransactionRunner =
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

    @Bean
    fun identityMailTasks() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 2
            setQueueCapacity(0)
            setThreadNamePrefix("hris-mail-")
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(10)
            setStrictEarlyShutdown(true)
        }

    @Bean
    fun identityMailTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("hris-mail-control-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(5)
        }

    @Bean
    @ConditionalOnProperty(name = ["HRIS_MAIL_ENABLED"], havingValue = "true")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'\${hris.worker.enabled:true}' == 'true'"
    )
    fun identityMailWorker(
        lease: dev.fajar.hris.identity.domain.usecases.LeaseIdentityMail,
        deliver: dev.fajar.hris.identity.domain.usecases.DeliverIdentityMail,
        @org.springframework.beans.factory.annotation.Qualifier("identityMailTasks")
        tasks: org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor,
        @org.springframework.beans.factory.annotation.Qualifier("identityMailTimers")
        timers: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
    ) = dev.fajar.hris.worker.mail.IdentityMailWorker(lease, deliver, tasks, timers)

    @Bean
    fun employeeImportPreviewTask(
        resolve: ResolveActor,
        advance: AdvanceEmployeeImportPreview,
        abort: AbortEmployeeImport,
    ): JobTask = EmployeeImportPreviewTask(resolve, advance, abort)

    @Bean
    fun employeeImportApplyTask(
        resolve: ResolveActor,
        advance: AdvanceEmployeeImportApply,
        abort: AbortEmployeeImport,
    ): JobTask = EmployeeImportApplyTask(resolve, advance, abort)

    @Bean
    fun objectCleanupTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-object-cleanup-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(30)
        }

    @Bean
    @ConditionalOnProperty(name = ["HRIS_STORAGE_ENABLED"], havingValue = "true")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'\${hris.worker.enabled:true}' == 'true'"
    )
    fun objectCleanupWorker(
        collect: dev.fajar.hris.storage.domain.usecases.CollectObjectGarbage,
        @org.springframework.beans.factory.annotation.Qualifier("objectCleanupTimers")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
    ) = dev.fajar.hris.worker.storage.ObjectCleanupWorker(collect, timer)

    @Bean
    fun documentValidationTask(
        resolve: ResolveActor,
        advance: AdvanceDocumentValidation,
        abort: AbortDocumentValidation,
    ): JobTask = DocumentValidationTask(resolve, advance, abort)

    @Bean
    fun documentInventoryTask(
        resolve: ResolveActor,
        advance: AdvanceDocumentInventory,
        abort: AbortDocumentInventory,
    ): JobTask = DocumentInventoryTask(resolve, advance, abort)

    @Bean
    fun mobileSyncTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-mobile-sync-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(15)
        }

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun mobileSyncWorker(
        maintain: dev.fajar.hris.sync.domain.usecases.MaintainMobileSync,
        @org.springframework.beans.factory.annotation.Qualifier("mobileSyncTimers")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
    ) = dev.fajar.hris.worker.sync.MobileSyncWorker(maintain, timer)

    @Bean
    fun leaveAccrualTask(
        resolve: ResolveActor,
        advance: AdvanceLeaveAccrualBatch,
        abort: AbortLeaveBatch,
    ): JobTask = LeaveAccrualTask(resolve, advance, abort)

    @Bean
    fun leaveYearCloseTask(
        resolve: ResolveActor,
        advance: AdvanceLeaveYearCloseBatch,
        abort: AbortLeaveBatch,
    ): JobTask = LeaveYearCloseTask(resolve, advance, abort)

    @Bean
    fun payrollCalculationTask(
        resolve: ResolveActor,
        advance: AdvancePayrollCalculation,
        abort: AbortPayrollCalculation,
    ): JobTask = PayrollCalculationTask(resolve, advance, abort)
}
