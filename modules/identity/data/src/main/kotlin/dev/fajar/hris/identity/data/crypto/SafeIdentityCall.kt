package dev.fajar.hris.identity.data.crypto

import dev.fajar.hris.core.database.databaseFailure
import dev.fajar.hris.core.domain.*
import java.util.concurrent.CancellationException
import org.slf4j.LoggerFactory

fun <T> safeIdentityCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(value)
    } catch (error: Exception) {
        if (error is InterruptedException || error is CancellationException) throw error
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        val failure =
            when (error) {
                is org.springframework.dao.DataAccessException -> databaseFailure(error)
                is org.jooq.exception.DataAccessException -> databaseFailure(error)
                is IdentityKeyUnavailableException ->
                    Failure(FailureKind.UNAVAILABLE, "identity_key_unavailable")
                else -> {
                    LoggerFactory.getLogger("dev.fajar.hris.identity")
                        .warn(
                            "Identity credential operation failed category={} frames={}",
                            error.javaClass.name,
                            error.stackTrace
                                .asSequence()
                                .filter { it.className.startsWith("dev.fajar.hris.") }
                                .take(8)
                                .joinToString(" | "),
                        )
                    Failure(FailureKind.UNEXPECTED, "identity_credential_failed")
                }
            }
        Result.Failed(failure)
    }
