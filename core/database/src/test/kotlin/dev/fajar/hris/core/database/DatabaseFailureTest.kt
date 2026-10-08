package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.sql.SQLException
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DatabaseFailureTest {
    @Test
    fun mapperFailuresStayInsideTheBoundaryAndDiagnosticsExcludeRawValues() {
        val logger =
            org.slf4j.LoggerFactory.getLogger("dev.fajar.hris.database")
                as ch.qos.logback.classic.Logger
        val appender =
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            val result =
                safeDatabaseCall<Unit> { throw IllegalArgumentException("private-record-secret") }
            assertEquals(Result.Failed(Failure(FailureKind.UNEXPECTED, "database_failure")), result)
            val sql =
                safeDatabaseCall<Unit> {
                    throw org.springframework.dao.DataIntegrityViolationException(
                        "sensitive query",
                        SQLException("private-record-secret", "23514"),
                    )
                }
            assertEquals(Result.Failed(Failure(FailureKind.CONFLICT, "data_conflict")), sql)
            val logged = appender.list.joinToString { it.formattedMessage }
            assertTrue(logged.contains("23514"))
            assertTrue(logged.contains("DatabaseFailureTest"))
            assertFalse(logged.contains("private-record-secret"))
            assertFalse(logged.contains("sensitive query"))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun cancellationAndLateInterruptionNeverBecomeSuccessfulResults() {
        assertThrows(CancellationException::class.java) {
            safeDatabaseCall<Unit> { throw CancellationException() }
        }
        try {
            assertThrows(InterruptedException::class.java) {
                safeDatabaseCall {
                    Thread.currentThread().interrupt()
                    1
                }
            }
        } finally {
            Thread.interrupted()
        }
        try {
            assertThrows(InterruptedException::class.java) {
                safeDatabaseCall<Unit> {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("late")
                }
            }
        } finally {
            Thread.interrupted()
        }
    }
}
