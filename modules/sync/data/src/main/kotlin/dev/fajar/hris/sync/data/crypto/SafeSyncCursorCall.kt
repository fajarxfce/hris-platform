package dev.fajar.hris.sync.data.crypto

import dev.fajar.hris.core.domain.*
import java.util.concurrent.CancellationException
import javax.crypto.AEADBadTagException
import org.slf4j.LoggerFactory
import tools.jackson.core.JacksonException

fun <T> safeSyncCursorCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(value)
    } catch (error: Exception) {
        val cancelled =
            generateSequence<Throwable>(error) { it.cause }
                .take(16)
                .firstOrNull { it is InterruptedException || it is CancellationException }
        if (cancelled is InterruptedException) {
            Thread.currentThread().interrupt()
            throw cancelled
        }
        if (cancelled is CancellationException) throw cancelled
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        Result.Failed(
            when (error) {
                is SyncCursorKeyUnavailableException ->
                    Failure(FailureKind.UNAVAILABLE, "sync_unavailable")
                is SyncCursorKeyRetiredException ->
                    Failure(FailureKind.CONFLICT, "sync_cursor_invalidated")
                is InvalidSyncCursorException,
                is AEADBadTagException,
                is JacksonException,
                is IllegalArgumentException ->
                    Failure(FailureKind.VALIDATION, "invalid_sync_cursor")
                else -> {
                    LoggerFactory.getLogger("dev.fajar.hris.sync")
                        .warn(
                            "Cursor operation failed category={} frames={}",
                            error.javaClass.name,
                            error.stackTrace
                                .asSequence()
                                .filter { it.className.startsWith("dev.fajar.hris.") }
                                .take(8)
                                .joinToString(" | "),
                        )
                    Failure(FailureKind.UNEXPECTED, "sync_cursor_failure")
                }
            }
        )
    }
