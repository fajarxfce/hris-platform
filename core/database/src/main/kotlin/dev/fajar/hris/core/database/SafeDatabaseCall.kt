package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.domain.Result
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessException

fun databaseFailure(error: DataAccessException): Failure {
    reportDatabaseFailure(error)
    if ((error.mostSpecificCause as? java.sql.SQLException)?.sqlState == "42501") {
        return Failure(FailureKind.FORBIDDEN, "access_denied")
    }
    if (
        (error.mostSpecificCause as? java.sql.SQLException)?.sqlState?.take(2) in
            setOf("08", "40", "53", "55", "57")
    )
        return Failure(FailureKind.UNAVAILABLE, "database_busy")
    return when (error) {
        is DataIntegrityViolationException -> Failure(FailureKind.CONFLICT, "data_conflict")
        is TransientDataAccessException -> Failure(FailureKind.UNAVAILABLE, "database_busy")
        else -> Failure(FailureKind.UNEXPECTED, "database_failure")
    }
}

fun databaseFailure(error: org.jooq.exception.DataAccessException): Failure {
    reportDatabaseFailure(error)
    return when {
        error.sqlState().startsWith("23") -> Failure(FailureKind.CONFLICT, "data_conflict")
        error.sqlState() == "42501" -> Failure(FailureKind.FORBIDDEN, "access_denied")
        error.sqlState().take(2) in setOf("08", "40", "53", "55", "57") ->
            Failure(FailureKind.UNAVAILABLE, "database_busy")
        else -> Failure(FailureKind.UNEXPECTED, "database_failure")
    }
}

/**
 * Records category and call frames, deliberately excluding SQL/driver messages and bound values.
 */
fun reportDatabaseFailure(error: Exception) {
    val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
    val state =
        causes
            .filterIsInstance<java.sql.SQLException>()
            .firstNotNullOfOrNull { it.sqlState }
            ?.takeIf { it.matches(Regex("[0-9A-Z]{5}")) }
    val frames =
        causes
            .flatMap { it.stackTrace.asList() }
            .asSequence()
            .filter { it.className.startsWith("dev.fajar.hris.") }
            .distinct()
            .take(12)
            .joinToString(" | ")
    LoggerFactory.getLogger("dev.fajar.hris.database")
        .warn(
            "Database operation failed category={} sqlState={} frames={}",
            error.javaClass.name,
            state,
            frames,
        )
}

fun <T> safeDatabaseCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(value)
    } catch (error: DataAccessException) {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        Result.Failed(databaseFailure(error))
    } catch (error: org.jooq.exception.DataAccessException) {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        Result.Failed(databaseFailure(error))
    } catch (error: Exception) {
        if (error is java.util.concurrent.CancellationException || error is InterruptedException)
            throw error
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        reportDatabaseFailure(error)
        Result.Failed(Failure(FailureKind.UNEXPECTED, "database_failure"))
    }
