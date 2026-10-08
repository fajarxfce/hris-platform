package dev.fajar.hris.storage.data.errors

import dev.fajar.hris.core.domain.*
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.model.S3Exception

fun <T> safeObjectStorageCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(value)
    } catch (error: Exception) {
        var cause: Throwable? = error
        val causes = mutableListOf<Throwable>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        for (ignored in 0 until 16) {
            val current = cause ?: break
            if (!seen.add(current)) break
            causes += current
            if (current is InterruptedException || current is CancellationException) throw current
            cause = current.cause
        }
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        val category =
            causes.firstOrNull {
                it is StoredObjectVersionChanged || it is InvalidObjectStorageResponse
            } ?: error
        val failure =
            when (category) {
                is ObjectStorageUnavailable ->
                    Failure(FailureKind.UNAVAILABLE, "object_storage_not_configured")
                is ObjectStorageCapacityExceeded ->
                    Failure(FailureKind.UNAVAILABLE, "object_storage_busy")
                is StoredObjectVersionChanged ->
                    Failure(FailureKind.CONFLICT, "stored_object_changed")
                is S3Exception ->
                    when (category.statusCode()) {
                        404 -> Failure(FailureKind.NOT_FOUND, "stored_object_not_found")
                        409,
                        412 -> Failure(FailureKind.CONFLICT, "stored_object_changed")
                        416 -> Failure(FailureKind.VALIDATION, "invalid_object_range")
                        else -> Failure(FailureKind.UNAVAILABLE, "object_storage_unavailable")
                    }
                is IOException,
                is SdkClientException ->
                    Failure(FailureKind.UNAVAILABLE, "object_storage_unavailable")
                is InvalidObjectStorageResponse ->
                    Failure(FailureKind.UNAVAILABLE, "invalid_storage_response")
                is IllegalArgumentException ->
                    Failure(FailureKind.VALIDATION, "invalid_object_request")
                else -> Failure(FailureKind.UNEXPECTED, "object_storage_failure")
            }
        if (
            failure.kind in setOf(FailureKind.UNEXPECTED, FailureKind.UNAVAILABLE) &&
                error !is ObjectStorageUnavailable &&
                error !is ObjectStorageCapacityExceeded
        ) {
            val frames =
                error.stackTrace
                    .asSequence()
                    .filter { it.className.startsWith("dev.fajar.hris.") }
                    .take(12)
                    .joinToString(" | ")
            LoggerFactory.getLogger("dev.fajar.hris.storage")
                .warn("Object storage failed category={} frames={}", error.javaClass.name, frames)
        }
        Result.Failed(failure)
    }
