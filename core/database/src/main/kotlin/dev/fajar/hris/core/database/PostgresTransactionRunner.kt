package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.TransactionRunner
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class PostgresTransactionRunner(
    manager: PlatformTransactionManager,
    private val jdbc: JdbcTemplate,
    private val statementTimeout: java.time.Duration = java.time.Duration.ofSeconds(15),
    private val lockTimeout: java.time.Duration = java.time.Duration.ofSeconds(5),
) : TransactionRunner, dev.fajar.hris.core.domain.CrossCompanyTransactionRunner {
    init {
        require(statementTimeout.toMillis() in 1..300_000)
        require(lockTimeout.toMillis() in 1..statementTimeout.toMillis())
    }

    private val transaction = TransactionTemplate(manager).apply { timeout = 30 }

    override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> =
        runScoped(actor, null, operation)

    override fun <T> run(
        actor: Actor,
        secondaryCompanyId: java.util.UUID,
        operation: () -> Result<T>,
    ): Result<T> {
        require(actor.companyId != null && actor.companyId != secondaryCompanyId)
        return runScoped(actor, secondaryCompanyId, operation)
    }

    private fun <T> runScoped(
        actor: Actor,
        secondaryCompanyId: java.util.UUID?,
        operation: () -> Result<T>,
    ): Result<T> =
        try {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            check(
                !org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()
            ) {
                "Use cases must not nest transaction owners"
            }
            checkNotNull(
                transaction.execute { status ->
                    jdbc.queryForObject(
                        "select set_config('statement_timeout', ?, true)",
                        String::class.java,
                        "${statementTimeout.toMillis()}ms",
                    )
                    jdbc.queryForObject(
                        "select set_config('lock_timeout', ?, true)",
                        String::class.java,
                        "${lockTimeout.toMillis()}ms",
                    )
                    jdbc.queryForObject(
                        "select set_config('idle_in_transaction_session_timeout', '30000ms', true)",
                        String::class.java,
                    )
                    jdbc.queryForObject(
                        "select set_config('hris.company_id', ?, true)",
                        String::class.java,
                        actor.companyId?.toString() ?: "",
                    )
                    jdbc.queryForObject(
                        "select set_config('hris.actor_id', ?, true)",
                        String::class.java,
                        actor.accountId.toString(),
                    )
                    jdbc.queryForObject(
                        "select set_config('hris.secondary_company_id', ?, true)",
                        String::class.java,
                        secondaryCompanyId?.toString() ?: "",
                    )
                    val result = operation()
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    if (result is Result.Failed) status.setRollbackOnly()
                    result
                }
            )
        } catch (error: java.lang.reflect.UndeclaredThrowableException) {
            // Spring's Java callback wraps checked exceptions after rollback.
            val interrupted = error.undeclaredThrowable
            if (interrupted is InterruptedException) throw interrupted
            throw error
        } catch (error: DataAccessException) {
            if (Thread.currentThread().isInterrupted)
                throw InterruptedException().apply { initCause(error) }
            Result.Failed(databaseFailure(error))
        } catch (error: org.springframework.transaction.TransactionTimedOutException) {
            if (Thread.currentThread().isInterrupted)
                throw InterruptedException().apply { initCause(error) }
            reportDatabaseFailure(error)
            Result.Failed(
                dev.fajar.hris.core.domain.Failure(
                    dev.fajar.hris.core.domain.FailureKind.UNAVAILABLE,
                    "database_busy",
                )
            )
        }
}
