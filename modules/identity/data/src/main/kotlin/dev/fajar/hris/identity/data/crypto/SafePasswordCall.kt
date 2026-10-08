package dev.fajar.hris.identity.data.crypto

import dev.fajar.hris.core.domain.*
import java.util.concurrent.CancellationException
import java.util.concurrent.RejectedExecutionException
import org.slf4j.LoggerFactory

fun <T> safePasswordCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val result = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(result)
    } catch (error: RejectedExecutionException) {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "password_verification_busy"))
    } catch (error: Exception) {
        if (error is CancellationException || error is InterruptedException) throw error
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        LoggerFactory.getLogger("dev.fajar.hris.identity")
            .warn(
                "Password operation failed category={} frames={}",
                error.javaClass.name,
                error.stackTrace
                    .asSequence()
                    .filter { it.className.startsWith("dev.fajar.hris.") }
                    .take(8)
                    .joinToString(" | "),
            )
        Result.Failed(Failure(FailureKind.UNEXPECTED, "password_operation_failed"))
    }
