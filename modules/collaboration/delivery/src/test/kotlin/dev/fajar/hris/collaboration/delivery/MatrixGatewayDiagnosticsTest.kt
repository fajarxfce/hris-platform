package dev.fajar.hris.collaboration.delivery

import dev.fajar.hris.collaboration.delivery.matrix.MatrixGatewayOperation
import dev.fajar.hris.collaboration.delivery.matrix.matrixGatewayProblem
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixGatewayDiagnosticsTest {
    @Test
    fun deniedResponsesRetainTheirContractWhileDiagnosticsExcludePrivateDetails() {
        val logger =
            org.slf4j.LoggerFactory.getLogger("dev.fajar.hris.collaboration.gateway")
                as ch.qos.logback.classic.Logger
        val appender =
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            val response =
                matrixGatewayProblem(
                    Failure(
                        FailureKind.FORBIDDEN,
                        "collaboration_recipient_unavailable",
                        parameters = mapOf("private-token" to "private-message"),
                    )
                )
            assertEquals(403, response.statusCode.value())
            val body = response.body as Map<*, *>
            assertEquals("M_FORBIDDEN", body["errcode"])
            assertEquals("collaboration_recipient_unavailable", body["org.hris.error_code"])
            assertEquals("no-store", response.headers.cacheControl)
            assertEquals(
                "MatrixGatewayRejected status=403 reason=collaboration_recipient_unavailable operation=general",
                appender.list.single().formattedMessage,
            )

            val unknown = matrixGatewayProblem(Failure(FailureKind.FORBIDDEN, "private-code"))
            assertEquals(403, unknown.statusCode.value())
            assertEquals("private-code", (unknown.body as Map<*, *>)["org.hris.error_code"])
            assertEquals(
                "MatrixGatewayRejected status=403 reason=other operation=general",
                appender.list.last().formattedMessage,
            )
            matrixGatewayProblem(Failure(FailureKind.UNAVAILABLE, "private-unavailable"))
            assertEquals(2, appender.list.size)
            for (operation in
                listOf(MatrixGatewayOperation.RoomSend, MatrixGatewayOperation.ToDevice)) {
                val classified =
                    matrixGatewayProblem(
                        Failure(FailureKind.FORBIDDEN, "collaboration_recipient_unavailable"),
                        operation,
                    )
                assertEquals(response.body, classified.body)
                assertEquals(
                    "MatrixGatewayRejected status=403 reason=collaboration_recipient_unavailable operation=${operation.label}",
                    appender.list.last().formattedMessage,
                )
            }
            for (event in appender.list) {
                assertFalse(event.formattedMessage.contains("private-"))
                assertNull(event.throwableProxy)
            }
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
