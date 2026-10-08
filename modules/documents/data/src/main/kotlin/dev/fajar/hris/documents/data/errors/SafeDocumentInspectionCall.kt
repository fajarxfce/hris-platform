package dev.fajar.hris.documents.data.errors

import dev.fajar.hris.core.domain.*
import java.util.concurrent.CancellationException
import org.slf4j.LoggerFactory

fun <T> safeDocumentInspectionCall(operation: () -> Result<T>): Result<T> =
    try {
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        value
    } catch (error: Exception) {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
        causes.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
        if (
            Thread.currentThread().isInterrupted ||
                causes.any {
                    it is InterruptedException || it is java.nio.channels.ClosedByInterruptException
                }
        ) {
            Thread.currentThread().interrupt()
            throw InterruptedException().apply { initCause(error) }
        }
        LoggerFactory.getLogger("DocumentInspection")
            .warn(
                "Document inspection failed category={} frames={}",
                error.javaClass.name,
                error.stackTrace
                    .filter { it.className.startsWith("dev.fajar.hris.") }
                    .take(5)
                    .map { "${it.className}.${it.methodName}:${it.lineNumber}" },
            )
        val failure =
            when {
                error is DocumentScannerNotConfigured ->
                    Failure(FailureKind.UNAVAILABLE, "document_scanner_not_configured")
                error is DocumentScannerCapacityException ->
                    Failure(FailureKind.UNAVAILABLE, "document_scanner_busy")
                error is DocumentScannerProtocolException ->
                    Failure(FailureKind.UNEXPECTED, "document_scanner_protocol_failure")
                error is java.io.IOException ->
                    Failure(FailureKind.UNAVAILABLE, "document_inspection_unavailable")
                else -> Failure(FailureKind.UNEXPECTED, "document_inspection_failure")
            }
        Result.Failed(failure)
    }
