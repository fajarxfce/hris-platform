package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.collaboration.delivery.matrix.matrixGatewayProblem
import dev.fajar.hris.collaboration.delivery.requests.matrixKeyQueryRequest
import dev.fajar.hris.collaboration.delivery.requests.matrixKeyUploadRequest
import dev.fajar.hris.collaboration.domain.policies.collaborationGatewayAuthenticationRequired
import dev.fajar.hris.collaboration.domain.usecases.AccessNativeCollaborationMatrixDevice
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.map
import dev.fajar.hris.core.http.*
import jakarta.servlet.http.HttpServletRequest
import java.time.Duration
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@RestController
class MatrixGatewayKeyController(private val device: AccessNativeCollaborationMatrixDevice) {
    private val parser =
        JsonMapper.builder(
                JsonFactory.builder()
                    .streamReadConstraints(
                        StreamReadConstraints.builder()
                            .maxNestingDepth(8)
                            .maxStringLength(512)
                            .maxNumberLength(16)
                            .build()
                    )
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build()
            )
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

    @PostMapping("/_matrix/client/v3/keys/upload")
    fun upload(request: HttpServletRequest): ResponseEntity<Any> =
        keyRequest(request, ::matrixKeyUploadRequest) { token, correlation, upload ->
            device.uploadKeys(token, correlation, upload).map {
                mapOf("one_time_key_counts" to it.oneTimeKeyCounts)
            }
        }

    @PostMapping("/_matrix/client/v3/keys/query")
    fun query(request: HttpServletRequest): ResponseEntity<Any> =
        keyRequest(request, ::matrixKeyQueryRequest) { token, correlation, query ->
            device.queryKeys(token, correlation, query).map { result ->
                mapOf(
                    "failures" to emptyMap<String, Any>(),
                    "device_keys" to
                        result.deviceKeys.mapValues { (_, devices) ->
                            devices.mapValues { (_, key) ->
                                mapOf(
                                    "user_id" to key.userId,
                                    "device_id" to key.deviceId,
                                    "algorithms" to key.algorithms,
                                    "keys" to key.keys,
                                    "signatures" to key.signatures,
                                )
                            }
                        },
                )
            }
        }

    private fun <T> keyRequest(
        request: HttpServletRequest,
        decode: (JsonNode) -> T,
        operation: (String, UUID, T) -> Result<Map<String, Any>>,
    ): ResponseEntity<Any> {
        if (request.queryString != null) return problem(400, "M_INVALID_PARAM")
        val authorization = request.getHeaders("Authorization")
        val header = if (authorization.hasMoreElements()) authorization.nextElement() else ""
        if (authorization.hasMoreElements() || !header.startsWith("Bearer ", ignoreCase = true))
            return matrixGatewayProblem(collaborationGatewayAuthenticationRequired().failure)
        if (
            request.contentType?.substringBefore(';')?.trim()?.lowercase() != "application/json" ||
                request.getHeader("Content-Encoding")?.equals("identity", ignoreCase = true) ==
                    false
        )
            return problem(415, "M_NOT_JSON")
        if (request.contentLengthLong > 65536) return problem(413, "M_TOO_LARGE")
        val input =
            try {
                val bytes =
                    BoundedRequestStream(request.inputStream, 65536, Duration.ofSeconds(2)).use {
                        it.readNBytes(65537)
                    }
                decode(requireNotNull(parser.readTree(bytes)))
            } catch (_: RequestBodyTooLargeException) {
                return problem(413, "M_TOO_LARGE")
            } catch (_: RequestBodyTimeoutException) {
                return problem(408, "M_BAD_JSON")
            } catch (_: java.net.SocketTimeoutException) {
                return problem(408, "M_BAD_JSON")
            } catch (error: Exception) {
                var cause: Throwable = error
                repeat(8) {
                    if (
                        cause is InterruptedException ||
                            cause is java.util.concurrent.CancellationException
                    )
                        throw cause
                    cause = cause.cause ?: cause
                }
                if (Thread.currentThread().isInterrupted)
                    throw InterruptedException().apply { initCause(error) }
                return problem(400, "M_BAD_JSON")
            }
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        return when (val result = operation(header.substring(7), correlation, input)) {
            is Result.Failed -> matrixGatewayProblem(result.failure)
            is Result.Success ->
                ResponseEntity.ok().header("Cache-Control", "no-store").body(result.value)
        }
    }

    private fun problem(status: Int, code: String): ResponseEntity<Any> =
        ResponseEntity.status(status)
            .header("Cache-Control", "no-store")
            .body(mapOf("errcode" to code, "error" to "Invalid Matrix key request."))
}
