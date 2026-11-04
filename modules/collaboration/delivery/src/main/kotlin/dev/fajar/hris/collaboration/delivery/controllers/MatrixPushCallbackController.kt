package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.collaboration.delivery.di.MatrixPushCallbackSettings
import dev.fajar.hris.collaboration.delivery.matrix.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.http.*
import jakarta.servlet.http.HttpServletRequest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Semaphore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Restricted provider ingress; neither browser/native sessions nor forwarded headers grant entry.
 */
@RestController
class MatrixPushCallbackController(
    private val settings: MatrixPushCallbackSettings,
    private val executor: MatrixPushCallbackExecutor,
) {
    private val bodies = Semaphore(4)

    @PostMapping("/_matrix/push/v1/notify")
    fun notify(request: HttpServletRequest): ResponseEntity<Any> {
        if (settings.sources?.allows(request.remoteAddr) != true) return problem(403, "M_FORBIDDEN")
        if (request.queryString != null) return problem(400, "M_INVALID_PARAM")
        if (
            request.contentType?.substringBefore(';')?.trim()?.lowercase() != "application/json" ||
                request.getHeader("Content-Encoding") != null
        )
            return problem(415, "M_NOT_JSON")
        if (request.contentLengthLong > MATRIX_PUSH_MAXIMUM_BYTES)
            return problem(413, "M_TOO_LARGE")
        if (!bodies.tryAcquire()) return problem(503, "M_UNKNOWN")
        val hint =
            try {
                val bytes =
                    BoundedRequestStream(
                            request.inputStream,
                            MATRIX_PUSH_MAXIMUM_BYTES.toLong(),
                            Duration.ofSeconds(2),
                        )
                        .use { it.readNBytes(MATRIX_PUSH_MAXIMUM_BYTES + 1) }
                matrixPushNotification(bytes) ?: return problem(400, "M_BAD_JSON")
            } catch (_: RequestBodyTooLargeException) {
                return problem(413, "M_TOO_LARGE")
            } catch (_: RequestBodyTimeoutException) {
                return problem(408, "M_BAD_JSON")
            } catch (_: java.net.SocketTimeoutException) {
                return problem(408, "M_BAD_JSON")
            } catch (error: java.io.IOException) {
                if (Thread.currentThread().isInterrupted)
                    throw InterruptedException().apply { initCause(error) }
                return problem(400, "M_BAD_JSON")
            } finally {
                bodies.release()
            }
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        return when (val result = executor.accept(hint, correlation)) {
            is Result.Failed -> matrixGatewayProblem(result.failure)
            is Result.Success ->
                ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(mapOf("rejected" to result.value.rejected))
        }
    }

    private fun problem(status: Int, code: String): ResponseEntity<Any> =
        ResponseEntity.status(status)
            .header("Cache-Control", "no-store")
            .header("Connection", "close")
            .body(mapOf("errcode" to code, "error" to "The push callback could not be accepted."))
}
