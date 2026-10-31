package dev.fajar.hris.collaboration.delivery.controllers

import com.fasterxml.jackson.annotation.JsonProperty
import dev.fajar.hris.collaboration.delivery.matrix.matrixGatewayProblem
import dev.fajar.hris.collaboration.domain.entities.CollaborationGatewayTokens
import dev.fajar.hris.collaboration.domain.usecases.RefreshNativeCollaborationGateway
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import jakarta.servlet.http.HttpServletRequest
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

@RestController
class MatrixGatewayRefreshController(
    private val refresh: RefreshNativeCollaborationGateway,
    private val clock: Clock,
) {
    private val parser =
        JsonMapper.builder(
                JsonFactory.builder()
                    .streamReadConstraints(
                        StreamReadConstraints.builder()
                            .maxNestingDepth(4)
                            .maxStringLength(512)
                            .maxNumberLength(16)
                            .build()
                    )
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build()
            )
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

    @PostMapping("/_matrix/client/v3/refresh")
    fun refresh(request: HttpServletRequest): ResponseEntity<Any> {
        if (request.contentType?.substringBefore(';')?.trim()?.lowercase() != "application/json")
            return problem(415, "M_NOT_JSON")
        if (request.contentLengthLong > 2048) return problem(413, "M_TOO_LARGE")
        val token =
            try {
                val bytes =
                    BoundedRequestStream(request.inputStream, 2048, Duration.ofSeconds(2)).use {
                        it.readNBytes(2049)
                    }
                val body = parser.readTree(bytes)
                if (
                    body == null ||
                        !body.isObject ||
                        body.propertyNames().toSet() != setOf("refresh_token") ||
                        !body["refresh_token"].isString
                )
                    return problem(400, "M_BAD_JSON")
                body["refresh_token"].asString()
            } catch (_: RequestBodyTooLargeException) {
                return problem(413, "M_TOO_LARGE")
            } catch (_: RequestBodyTimeoutException) {
                return problem(408, "M_BAD_JSON")
            } catch (_: java.net.SocketTimeoutException) {
                return problem(408, "M_BAD_JSON")
            } catch (error: Exception) {
                if (
                    error is InterruptedException ||
                        error is java.util.concurrent.CancellationException
                )
                    throw error
                if (Thread.currentThread().isInterrupted)
                    throw InterruptedException().apply { initCause(error) }
                return problem(400, "M_BAD_JSON")
            }
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        return when (val result = refresh.execute(token, correlation)) {
            is Result.Failed -> matrixGatewayProblem(result.failure)
            is Result.Success ->
                ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(MatrixGatewayRefreshResponse(result.value, clock))
        }
    }

    private fun problem(status: Int, code: String): ResponseEntity<Any> =
        ResponseEntity.status(status)
            .header("Cache-Control", "no-store")
            .body(mapOf("errcode" to code, "error" to "Invalid Matrix refresh request."))
}

class MatrixGatewayRefreshResponse(tokens: CollaborationGatewayTokens, clock: Clock) {
    @get:JsonProperty("access_token") val accessToken = tokens.accessToken
    @get:JsonProperty("refresh_token") val refreshToken = tokens.refreshToken
    @get:JsonProperty("expires_in_ms")
    val expiresInMs =
        Duration.between(clock.instant(), tokens.accessExpiresAt).toMillis().coerceAtLeast(0)

    override fun toString() = "MatrixGatewayRefreshResponse(<redacted>)"
}
