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
) : TransactionRunner {
    private val transaction = TransactionTemplate(manager)

    override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> =
        try {
            check(
                !org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()
            ) {
                "Use cases must not nest transaction owners"
            }
            checkNotNull(
                transaction.execute { status ->
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
                    val result = operation()
                    if (result is Result.Failed) status.setRollbackOnly()
                    result
                }
            )
        } catch (error: DataAccessException) {
            Result.Failed(databaseFailure(error))
        }
}
