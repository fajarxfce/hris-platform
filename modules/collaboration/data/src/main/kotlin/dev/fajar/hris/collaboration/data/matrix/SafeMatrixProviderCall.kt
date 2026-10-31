package dev.fajar.hris.collaboration.data.matrix

import dev.fajar.hris.core.domain.*
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class MatrixDeadlineException : RuntimeException("Matrix login observation expired")

class MatrixResponseException : IOException("Invalid Matrix response")

class MatrixStatusException(val status: Int) : IOException("Matrix request rejected")

class MatrixCapacityException : RuntimeException("Matrix exchange capacity exhausted")

/** Maps acquisition and parsing together; never logs provider messages, bodies or bearers. */
fun <T> safeMatrixProviderCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(value)
    } catch (error: Exception) {
        var cause: Throwable = error
        repeat(8) {
            if (cause is ExecutionException || cause is CompletionException) {
                cause = cause.cause ?: cause
            }
        }
        if (cause is InterruptedException) throw cause
        if (cause is CancellationException) throw cause
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Failed(
            when (cause) {
                is MatrixCapacityException ->
                    Failure(
                        FailureKind.RATE_LIMITED,
                        "collaboration_provider_busy",
                        parameters = mapOf("retryAfterSeconds" to "1"),
                    )
                is MatrixDeadlineException ->
                    Failure(FailureKind.CONFLICT, "collaboration_admission_changed")
                is MatrixResponseException ->
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response")
                is MatrixStatusException ->
                    Failure(
                        FailureKind.UNAVAILABLE,
                        if (cause.status == 429) "collaboration_provider_busy"
                        else "collaboration_provider_rejected",
                    )
                is IOException,
                is TimeoutException ->
                    Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
                else -> Failure(FailureKind.UNEXPECTED, "collaboration_provider_failed")
            }
        )
    }
