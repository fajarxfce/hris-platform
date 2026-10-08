package dev.fajar.hris.people.data.errors

import dev.fajar.hris.core.domain.*
import java.io.IOException
import java.io.UncheckedIOException
import java.util.concurrent.CancellationException
import org.slf4j.LoggerFactory

fun <T> safeCsvCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(value)
    } catch (error: Exception) {
        if (error is InterruptedException || error is CancellationException) throw error
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        if (
            error is IOException ||
                error is UncheckedIOException ||
                error is IllegalArgumentException
        )
            Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employee_csv"))
        else {
            val frames =
                error.stackTrace
                    .asSequence()
                    .filter { it.className.startsWith("dev.fajar.hris.") }
                    .take(12)
                    .joinToString(" | ")
            LoggerFactory.getLogger("dev.fajar.hris.people.csv")
                .warn("CSV acquisition failed category={} frames={}", error.javaClass.name, frames)
            Result.Failed(Failure(FailureKind.UNEXPECTED, "employee_csv_failure"))
        }
    }
