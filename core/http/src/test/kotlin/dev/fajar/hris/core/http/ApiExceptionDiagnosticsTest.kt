package dev.fajar.hris.core.http

import jakarta.servlet.ServletException
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiExceptionDiagnosticsTest {
    @Test
    fun wrappedFailuresKeepBoundedCategoriesAndCallSitesWithoutPrivateMessages() {
        val logger =
            org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler::class.java)
                as ch.qos.logback.classic.Logger
        val appender =
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            val root = AbstractMethodError("private-personnel-data")
            root.stackTrace =
                arrayOf(
                    StackTraceElement("dev.fajar.hris.payroll.Fixture", "execute", "Fixture.kt", 23)
                )
            val response =
                ApiExceptionHandler().unexpected(ServletException("private-request-data", root))
            assertEquals(500, response.status)
            assertEquals("unexpected_error", response.properties?.get("code"))
            val logged = appender.list.single()
            assertTrue(logged.formattedMessage.contains("AbstractMethodError"))
            assertTrue(logged.formattedMessage.contains("Fixture.kt:23"))
            assertFalse(logged.formattedMessage.contains("private-"))
            assertNull(logged.throwableProxy)
            val cycle = IllegalStateException("private-cycle")
            val nested = IllegalStateException("private-nested", cycle)
            cycle.initCause(nested)
            assertEquals(500, ApiExceptionHandler().unexpected(cycle).status)
            assertTrue(appender.list.last().formattedMessage.length < 6000)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun wrappedCancellationAndInterruptionPropagateInsteadOfBecomingProblems() {
        val handler = ApiExceptionHandler()
        val cancelled = CancellationException("cancelled")
        assertSame(
            cancelled,
            assertThrows(CancellationException::class.java) {
                handler.unexpected(ServletException(cancelled))
            },
        )
        val interrupted = InterruptedException("interrupted")
        try {
            assertSame(
                interrupted,
                assertThrows(InterruptedException::class.java) {
                    handler.unexpected(ServletException(interrupted))
                },
            )
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
