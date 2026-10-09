package dev.fajar.hris.push.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.data.transport.PushResponseSizeException
import java.io.IOException
import java.util.concurrent.CancellationException
import org.slf4j.LoggerFactory

fun <T> safePushCall(operation: () -> Result<T>): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        value
    } catch (error: Exception) {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
        causes
            .firstOrNull { it is InterruptedException || it is CancellationException }
            ?.let { throw it }
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        val failure =
            when {
                causes.any { it is PushResponseSizeException } ->
                    Failure(FailureKind.UNEXPECTED, "push_response_invalid")
                causes.any { it is IOException } ->
                    Failure(FailureKind.UNAVAILABLE, "push_delivery_unavailable")
                else -> Failure(FailureKind.UNEXPECTED, "push_delivery_failed")
            }
        LoggerFactory.getLogger("dev.fajar.hris.push")
            .warn(
                "Push operation failed code={} categories={} frames={}",
                failure.code,
                causes.map { it.javaClass.name }.distinct(),
                causes
                    .asSequence()
                    .flatMap { it.stackTrace.asSequence() }
                    .filter { it.className.startsWith("dev.fajar.hris.") }
                    .distinct()
                    .take(8)
                    .joinToString(" | "),
            )
        Result.Failed(failure)
    }
