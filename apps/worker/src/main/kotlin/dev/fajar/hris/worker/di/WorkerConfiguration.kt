package dev.fajar.hris.worker.di

import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.communications.domain.usecases.*
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
    fun collaborationSpaceProvisioningSettings() =
        dev.fajar.hris.worker.collaboration.CollaborationSpaceProvisioningWorkerSettings()

    @Bean
    fun collaborationSpaceProvisioningTasks(
        settings: dev.fajar.hris.worker.collaboration.CollaborationSpaceProvisioningWorkerSettings
    ) =
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = settings.parallelism
            maxPoolSize = settings.parallelism
            setQueueCapacity(0)
            setThreadNamePrefix("hris-space-provisioning-")
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(20)
            setStrictEarlyShutdown(true)
        }

    @Bean
    fun collaborationSpaceProvisioningTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("hris-space-provisioning-control-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(5)
        }

    @Bean
    @ConditionalOnProperty(
        name =
            [
                "HRIS_COLLABORATION_SPACE_PROVISIONING_ENABLED",
                "HRIS_COLLABORATION_SPACE_ENABLED",
                "HRIS_COLLABORATION_ENABLED",
                "HRIS_MATRIX_ENABLED",
            ],
        havingValue = "true",
    )
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'\${hris.worker.enabled:true}' == 'true'"
    )
    fun collaborationSpaceProvisioningWorker(
        claim: ClaimCollaborationSpaceProvisioning,
        advance: AdvanceCollaborationSpaceProvisioning,
        settle: SettleCollaborationSpaceProvisioningFailure,
        @org.springframework.beans.factory.annotation.Qualifier(
            "collaborationSpaceProvisioningTasks"
        )
        tasks: org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor,
        @org.springframework.beans.factory.annotation.Qualifier(
            "collaborationSpaceProvisioningTimers"
        )
        timers: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
        settings: dev.fajar.hris.worker.collaboration.CollaborationSpaceProvisioningWorkerSettings,
    ) =
        dev.fajar.hris.worker.collaboration.CollaborationSpaceProvisioningWorker(
            { owner, limit -> claim.execute(owner, limit) },
            advance::execute,
            settle::execute,
            tasks,
            timers,
            settings,
        )

    @Bean
    fun collaborationSpaceRoomSettings() =
        dev.fajar.hris.worker.collaboration.CollaborationSpaceRoomWorkerSettings()

    @Bean
    fun collaborationSpaceRoomTasks(
        settings: dev.fajar.hris.worker.collaboration.CollaborationSpaceRoomWorkerSettings
    ) =
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = settings.parallelism
            maxPoolSize = settings.parallelism
            setQueueCapacity(0)
            setThreadNamePrefix("hris-space-room-")
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(20)
            setStrictEarlyShutdown(true)
        }

    @Bean
    fun collaborationSpaceRoomTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("hris-space-room-control-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(5)
        }

    @Bean
    @ConditionalOnProperty(
        name =
            [
                "HRIS_COLLABORATION_SPACE_ROOM_PROVISIONING_ENABLED",
                "HRIS_COLLABORATION_SPACE_ENABLED",
                "HRIS_COLLABORATION_ENABLED",
                "HRIS_MATRIX_ENABLED",
            ],
        havingValue = "true",
    )
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'\${hris.worker.enabled:true}' == 'true'"
    )
    fun collaborationSpaceRoomWorker(
        claim: ClaimCollaborationSpaceRooms,
        advance: AdvanceCollaborationSpaceRoom,
        settle: SettleCollaborationSpaceRoomFailure,
        @org.springframework.beans.factory.annotation.Qualifier("collaborationSpaceRoomTasks")
        tasks: org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor,
        @org.springframework.beans.factory.annotation.Qualifier("collaborationSpaceRoomTimers")
        timers: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
        settings: dev.fajar.hris.worker.collaboration.CollaborationSpaceRoomWorkerSettings,
    ) =
        dev.fajar.hris.worker.collaboration.CollaborationSpaceRoomWorker(
            { owner, limit -> claim.execute(owner, limit) },
            advance::execute,
            settle::execute,
            tasks,
            timers,
            settings,
        )

    @Bean
    @ConditionalOnProperty(
        name = ["HRIS_COLLABORATION_ENABLED", "HRIS_MATRIX_ENABLED"],
        havingValue = "true",
    )
    fun collaborationDirectRoomTask(
        advance: AdvanceNativeCollaborationDirectRoom,
        abort: AbortCollaborationProvisioning,
    ): JobTask = CollaborationDirectRoomTask(advance, abort)

    @Bean
    @ConditionalOnProperty(
        name = ["HRIS_COLLABORATION_ENABLED", "HRIS_MATRIX_ENABLED"],
        havingValue = "true",
    )
    fun collaborationProvisioningTask(
        advance: AdvanceNativeCollaborationProvisioning,
        abort: AbortCollaborationProvisioning,
    ): JobTask = CollaborationProvisioningTask(advance, abort)

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun reportExportCleanupTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-report-expiry-")
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
    fun reportExportCleanupWorker(
        maintain: dev.fajar.hris.reporting.domain.usecases.MaintainReportExports,
        @org.springframework.beans.factory.annotation.Qualifier("reportExportCleanupTimers")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
    ) = dev.fajar.hris.worker.reporting.ReportExportCleanupWorker(maintain, timer)

    @Bean
    fun reportExportTask(
        resolve: ResolveActor,
        advance: dev.fajar.hris.reporting.domain.usecases.AdvanceReportExport,
        abort: dev.fajar.hris.reporting.domain.usecases.AbortReportExport,
    ): JobTask = ReportExportTask(resolve, advance, abort)

    @Bean
    fun onboardingScheduleTask(
        resolve: ResolveActor,
        execute: ExecuteOnboardingSchedule,
        abort: AbortOnboardingSchedule,
    ): JobTask = OnboardingScheduleTask(resolve, execute, abort)

    @Bean
    fun offboardingScheduleTask(
        resolve: ResolveActor,
        execute: ExecuteOffboardingSchedule,
        abort: AbortOffboardingSchedule,
    ): JobTask = OffboardingScheduleTask(resolve, execute, abort)

    @Bean
    fun workPeriodCloseTask(
        resolve: ResolveActor,
        advance: AdvanceWorkPeriodClose,
        abort: AbortWorkPeriodClose,
    ): JobTask = WorkPeriodCloseTask(resolve, advance, abort)

    @Bean fun workerSettings() = WorkerSettings()

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled", "hris.native-session-cleanup.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun nativeSessionCleanupTimer() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-native-cleanup-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(15)
        }

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled", "hris.native-session-cleanup.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun nativeSessionCleanupWorker(
        maintain: dev.fajar.hris.identity.domain.usecases.MaintainNativeSessions,
        @org.springframework.beans.factory.annotation.Qualifier("nativeSessionCleanupTimer")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
        @org.springframework.beans.factory.annotation.Value(
            "\${hris.native-session-cleanup.interval-seconds:60}"
        )
        intervalSeconds: Long,
    ) =
        dev.fajar.hris.worker.identity.NativeSessionCleanupWorker(
            maintain,
            timer,
            java.time.Duration.ofSeconds(intervalSeconds),
        )

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled", "hris.collaboration-gateway-cleanup.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun collaborationGatewayCleanupTimer() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-collaboration-cache-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(15)
        }

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled", "hris.collaboration-gateway-cleanup.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun collaborationGatewayCleanupWorker(
        maintain: dev.fajar.hris.collaboration.domain.usecases.MaintainCollaborationGatewayReplays,
        maintainSpace:
            dev.fajar.hris.collaboration.domain.usecases.MaintainCollaborationSpaceTransientState,
        @org.springframework.beans.factory.annotation.Qualifier("collaborationGatewayCleanupTimer")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
        @org.springframework.beans.factory.annotation.Value(
            "\${hris.collaboration-gateway-cleanup.interval-seconds:5}"
        )
        intervalSeconds: Long,
    ) =
        dev.fajar.hris.worker.collaboration.CollaborationGatewayCleanupWorker(
            maintain,
            maintainSpace,
            timer,
            java.time.Duration.ofSeconds(intervalSeconds),
        )

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
    fun directoryProjectionTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-directory-projection-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(15)
        }

    @Bean
    fun integrationObservationTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-integration-observation-")
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
    fun integrationObservationWorker(
        publish: dev.fajar.hris.administration.domain.usecases.PublishWorkerIntegrationObservation,
        @org.springframework.beans.factory.annotation.Qualifier("integrationObservationTimers")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
    ) = dev.fajar.hris.worker.integrations.WorkerIntegrationReporter(publish, timer)

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun directoryProjectionWorker(
        refresh: RefreshCompanyDirectory,
        @org.springframework.beans.factory.annotation.Qualifier("directoryProjectionTimers")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
    ) = dev.fajar.hris.worker.sync.CompanyDirectoryWorker(refresh, timer)

    @Bean
    fun collaborationSummaryTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-collaboration-summary-")
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
    fun collaborationSummaryWorker(
        refresh: RefreshCollaborationSummaries,
        @org.springframework.beans.factory.annotation.Qualifier("collaborationSummaryTimers")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
    ) = dev.fajar.hris.worker.collaboration.CollaborationSummaryWorker(refresh, timer)

    @Bean
    fun selfProfileProjectionTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("hris-self-profile-")
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
    fun selfProfileProjectionWorker(
        refresh: RefreshSelfProfiles,
        @org.springframework.beans.factory.annotation.Qualifier("selfProfileProjectionTimers")
        timer: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
    ) = dev.fajar.hris.worker.sync.SelfProfileWorker(refresh, timer)

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
    fun leaveCarryExpiryTask(
        advance: AdvanceLeaveCarryExpiry,
        abort: AbortLeaveCarryExpiry,
    ): JobTask = LeaveCarryExpiryTask(advance, abort)

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

    @Bean
    fun payrollAmendmentPublicationTask(
        resolve: ResolveActor,
        advance: AdvancePayrollAmendmentPublication,
        abort: AbortPayrollAmendmentPublication,
    ): JobTask = PayrollAmendmentPublicationTask(resolve, advance, abort)

    @Bean
    fun payrollFinalizationTask(
        resolve: ResolveActor,
        advance: AdvancePayrollFinalization,
        abort: AbortPayrollFinalization,
    ): JobTask = PayrollFinalizationTask(resolve, advance, abort)

    @Bean
    fun announcementPublicationTask(
        resolve: ResolveActor,
        advance: AdvanceAnnouncementPublication,
        abort: AbortAnnouncementPublication,
    ): JobTask = AnnouncementPublicationTask(resolve, advance, abort)

    @Bean
    fun collaborationPushSettings() = dev.fajar.hris.worker.push.CollaborationPushWorkerSettings()

    @Bean
    fun collaborationPushTasks(
        settings: dev.fajar.hris.worker.push.CollaborationPushWorkerSettings
    ) =
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = settings.parallelism
            maxPoolSize = settings.parallelism
            setQueueCapacity(0)
            setThreadNamePrefix("hris-chat-push-")
            setWaitForTasksToCompleteOnShutdown(false)
            // The current SQL statement has its own15-second bound after interruption.
            setAwaitTerminationSeconds(20)
            setStrictEarlyShutdown(true)
        }

    @Bean
    fun collaborationPushTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("hris-chat-push-control-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(5)
        }

    @Bean
    fun collaborationSpacePushTasks(
        settings: dev.fajar.hris.worker.push.CollaborationPushWorkerSettings
    ) =
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = settings.parallelism
            maxPoolSize = settings.parallelism
            setQueueCapacity(0)
            setThreadNamePrefix("hris-group-chat-push-")
            setWaitForTasksToCompleteOnShutdown(false)
            // The current SQL statement has its own15-second bound after interruption.
            setAwaitTerminationSeconds(20)
            setStrictEarlyShutdown(true)
        }

    @Bean
    fun collaborationSpacePushTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("hris-group-chat-push-control-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(5)
        }

    @Bean
    @ConditionalOnProperty(
        name =
            [
                "HRIS_COLLABORATION_PUSH_DELIVERY_ENABLED",
                "HRIS_FCM_ENABLED",
                "HRIS_COLLABORATION_ENABLED",
                "HRIS_MATRIX_ENABLED",
                "HRIS_COLLABORATION_PUSH_ENABLED",
            ],
        havingValue = "true",
    )
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'\${hris.worker.enabled:true}' == 'true'"
    )
    fun collaborationPushWorker(
        handle: HandleCollaborationPush,
        @org.springframework.beans.factory.annotation.Qualifier("collaborationPushTasks")
        tasks: org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor,
        @org.springframework.beans.factory.annotation.Qualifier("collaborationPushTimers")
        timers: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
        settings: dev.fajar.hris.worker.push.CollaborationPushWorkerSettings,
    ) =
        dev.fajar.hris.worker.push.CollaborationPushWorker(
            handle::claim,
            handle::deliver,
            tasks,
            timers,
            settings,
        )

    @Bean
    @ConditionalOnProperty(
        name =
            [
                "HRIS_COLLABORATION_PUSH_DELIVERY_ENABLED",
                "HRIS_COLLABORATION_SPACE_ENABLED",
                "HRIS_FCM_ENABLED",
                "HRIS_COLLABORATION_ENABLED",
                "HRIS_MATRIX_ENABLED",
                "HRIS_COLLABORATION_PUSH_ENABLED",
            ],
        havingValue = "true",
    )
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'\${hris.worker.enabled:true}' == 'true'"
    )
    fun collaborationSpacePushWorker(
        handle: HandleCollaborationSpacePush,
        @org.springframework.beans.factory.annotation.Qualifier("collaborationSpacePushTasks")
        tasks: org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor,
        @org.springframework.beans.factory.annotation.Qualifier("collaborationSpacePushTimers")
        timers: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
        settings: dev.fajar.hris.worker.push.CollaborationPushWorkerSettings,
    ) =
        dev.fajar.hris.worker.push.CollaborationPushWorker(
            handle::claim,
            handle::deliver,
            tasks,
            timers,
            settings,
        )

    @Bean
    fun inboxPushSettings(policy: dev.fajar.hris.communications.domain.entities.InboxPushPolicy) =
        dev.fajar.hris.worker.push.InboxPushWorkerSettings(
            pollInterval =
                if (policy.enabled) java.time.Duration.ofMillis(250)
                else java.time.Duration.ofSeconds(2)
        )

    @Bean
    fun inboxPushTasks(settings: dev.fajar.hris.worker.push.InboxPushWorkerSettings) =
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = settings.parallelism
            maxPoolSize = settings.parallelism
            setQueueCapacity(0)
            setThreadNamePrefix("hris-push-")
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(10)
            setStrictEarlyShutdown(true)
        }

    @Bean
    fun inboxPushTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("hris-push-control-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(5)
        }

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun inboxPushWorker(
        lease: LeaseInboxPush,
        deliver: DeliverInboxPush,
        @org.springframework.beans.factory.annotation.Qualifier("inboxPushTasks")
        tasks: org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor,
        @org.springframework.beans.factory.annotation.Qualifier("inboxPushTimers")
        timers: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
        settings: dev.fajar.hris.worker.push.InboxPushWorkerSettings,
    ) = dev.fajar.hris.worker.push.InboxPushWorker(lease, deliver, tasks, timers, settings)

    @Bean
    fun inboxEmailSettings(policy: dev.fajar.hris.communications.domain.entities.InboxEmailPolicy) =
        dev.fajar.hris.worker.mail.InboxEmailWorkerSettings(
            pollInterval =
                if (policy.enabled) java.time.Duration.ofMillis(250)
                else java.time.Duration.ofSeconds(2)
        )

    @Bean
    fun inboxEmailTasks(settings: dev.fajar.hris.worker.mail.InboxEmailWorkerSettings) =
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = settings.parallelism
            maxPoolSize = settings.parallelism
            setQueueCapacity(0)
            setThreadNamePrefix("hris-inbox-email-")
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(10)
            setStrictEarlyShutdown(true)
        }

    @Bean
    fun inboxEmailTimers() =
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("hris-inbox-email-control-")
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(5)
        }

    @Bean
    @ConditionalOnProperty(
        name = ["hris.worker.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun inboxEmailWorker(
        lease: LeaseInboxEmail,
        deliver: DeliverInboxEmail,
        @org.springframework.beans.factory.annotation.Qualifier("inboxEmailTasks")
        tasks: org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor,
        @org.springframework.beans.factory.annotation.Qualifier("inboxEmailTimers")
        timers: org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler,
        settings: dev.fajar.hris.worker.mail.InboxEmailWorkerSettings,
    ) = dev.fajar.hris.worker.mail.InboxEmailWorker(lease, deliver, tasks, timers, settings)
}
