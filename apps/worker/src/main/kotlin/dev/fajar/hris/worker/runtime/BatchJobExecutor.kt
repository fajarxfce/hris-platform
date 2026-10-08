package dev.fajar.hris.worker.runtime

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobLease
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.DefaultTransactionAttribute

class BatchJobExecutor(
    private val operator: JobOperator,
    private val repository: JobRepository,
    private val transactions: PlatformTransactionManager,
    private val settings: WorkerSettings,
) {
    fun execute(lease: JobLease, task: JobTask): Result<Unit> {
        val started = System.nanoTime()
        var completed = lease.job.completedItems
        var steps = 0
        val tasklet = Tasklet { contribution, _ ->
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (System.nanoTime() - started >= settings.maximumRunTime.toNanos())
                throw BatchStepException(
                    Failure(FailureKind.UNAVAILABLE, "job_time_budget_exceeded")
                )
            steps++
            if (steps > lease.job.request.totalItems + 1)
                throw BatchStepException(Failure(FailureKind.UNEXPECTED, "job_did_not_progress"))
            val result = task.advance(lease)
            if (result is Result.Failed) {
                if (result.failure.code == "job_cancellation_requested")
                    throw org.springframework.batch.core.job.JobInterruptedException(
                        "job_cancellation_requested"
                    )
                throw BatchStepException(result.failure)
            }
            val next = (result as Result.Success).value
            if (
                next.completedItems !in completed..lease.job.request.totalItems ||
                    (!next.finished && next.completedItems <= completed) ||
                    (next.finished && next.completedItems != lease.job.request.totalItems)
            )
                throw BatchStepException(Failure(FailureKind.UNEXPECTED, "job_did_not_progress"))
            contribution.incrementWriteCount((next.completedItems - completed).toLong())
            completed = next.completedItems
            contribution.stepExecution.executionContext.putInt("completedItems", completed)
            if (next.finished) RepeatStatus.FINISHED else RepeatStatus.CONTINUABLE
        }
        val step =
            StepBuilder("process-items", repository)
                .tasklet(tasklet, transactions)
                .transactionAttribute(
                    object :
                        DefaultTransactionAttribute(
                            TransactionDefinition.PROPAGATION_NOT_SUPPORTED
                        ) {
                        // Batch suppresses checked exceptions unless the step marks them
                        // rollback-worthy.
                        // Business transactions remain owned by feature use cases.
                        override fun rollbackOn(error: Throwable): Boolean = true
                    }
                )
                .build()
        val job = JobBuilder(lease.job.request.kind.name, repository).start(step).build()
        val parameters =
            JobParametersBuilder()
                .addString("jobId", lease.job.request.id.toString())
                .addString("leaseId", lease.token.toString())
                .toJobParameters()
        return try {
            val execution = operator.start(job, parameters)
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (execution.status == BatchStatus.COMPLETED) Result.Success(Unit)
            else Result.Failed(batchFailure(execution.allFailureExceptions.firstOrNull()))
        } catch (error: InterruptedException) {
            throw error
        } catch (error: java.util.concurrent.CancellationException) {
            throw error
        } catch (error: Exception) {
            if (Thread.currentThread().isInterrupted)
                throw InterruptedException().apply { initCause(error) }
            Result.Failed(batchFailure(error))
        }
    }
}
