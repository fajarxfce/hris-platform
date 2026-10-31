package dev.fajar.hris.collaboration.data.matrix

import java.net.http.*
import java.time.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** The application owns the client; this datasource owns/cancels each bounded exchange. */
class HttpMatrixClientDataSource(
    private val client: HttpClient,
    private val configuration: MatrixProviderConfiguration,
    private val clock: Clock,
    maximumConcurrent: Int = 32,
) : MatrixClientDataSource {
    private val exchanges = Semaphore(maximumConcurrent)
    private val json =
        JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

    init {
        require(maximumConcurrent in 1..128)
        require(client.followRedirects() == HttpClient.Redirect.NEVER)
        require(client.connectTimeout().orElse(Duration.ofSeconds(6)) <= Duration.ofSeconds(5))
    }

    override fun login(jwt: String, deviceId: String, deadline: Instant): MatrixLoginData {
        require(jwt.length in 1..4096 && jwt.all { it.code in 33..126 })
        require(deviceId.matches(Regex("HRIS_[a-f0-9]{32}")))
        val startedAt = clock.instant()
        val remaining = Duration.between(startedAt, deadline)
        if (remaining <= Duration.ZERO) throw MatrixDeadlineException()
        val response =
            post(
                "/_matrix/client/v3/login",
                mapOf(
                    "type" to "org.matrix.login.jwt",
                    "token" to jwt,
                    "device_id" to deviceId,
                    "initial_device_display_name" to "HRIS",
                    "refresh_token" to false,
                ),
                null,
                minOf(configuration.requestTimeout, remaining),
            )
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        val body = parse(response)
        val lifetime = body.path("expires_in_ms")
        if (
            !lifetime.isIntegralNumber ||
                !lifetime.canConvertToLong() ||
                lifetime.asLong() !in 1..600000 ||
                !body.path("refresh_token").isMissingNode
        )
            throw MatrixResponseException()
        return MatrixLoginData(
            text(body, "user_id", 255),
            text(body, "device_id", 128),
            text(body, "access_token", 2048),
            startedAt.plusMillis(lifetime.asLong()),
        )
    }

    override fun logoutDevice(accessToken: String) {
        require(accessToken.length in 1..2048 && accessToken.all { it.code in 33..126 })
        val response =
            post("/_matrix/client/v3/logout", emptyMap(), accessToken, configuration.requestTimeout)
        if (response.statusCode() == 401) {
            if (parse(response).path("errcode").asString() == "M_UNKNOWN_TOKEN") return
        }
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        parse(response)
    }

    override fun whoAmI(accessToken: String, deadline: Instant): MatrixIdentityData {
        require(accessToken.length in 1..2048 && accessToken.all { it.code in 33..126 })
        val remaining = Duration.between(clock.instant(), deadline)
        if (remaining <= Duration.ZERO) throw MatrixDeadlineException()
        val timeout = minOf(configuration.requestTimeout, remaining)
        val request =
            HttpRequest.newBuilder(
                    configuration.origin.resolve("/_matrix/client/v3/account/whoami")
                )
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("Authorization", "Bearer $accessToken")
                .GET()
                .build()
        val response = exchange(request, timeout)
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        val body = parse(response)
        val guest = body.path("is_guest")
        if (!guest.isBoolean) throw MatrixResponseException()
        return MatrixIdentityData(
            text(body, "user_id", 255),
            text(body, "device_id", 128),
            guest.asBoolean(),
        )
    }

    private fun post(
        path: String,
        body: Map<String, Any>,
        bearer: String?,
        timeout: Duration,
    ): HttpResponse<ByteArray> {
        val builder =
            HttpRequest.newBuilder(configuration.origin.resolve(path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return exchange(builder.build(), timeout)
    }

    private fun exchange(request: HttpRequest, timeout: Duration): HttpResponse<ByteArray> {
        if (!exchanges.tryAcquire()) throw MatrixCapacityException()
        return try {
            val pending =
                client.sendAsync(
                    request,
                    HttpResponse.BodyHandler { BoundedMatrixBodySubscriber() },
                )
            try {
                pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS)
            } finally {
                if (!pending.isDone) pending.cancel(true)
            }
        } finally {
            exchanges.release()
        }
    }

    private fun parse(response: HttpResponse<ByteArray>): JsonNode {
        if (
            !response
                .headers()
                .firstValue("Content-Encoding")
                .orElse("identity")
                .equals("identity", true) ||
                !response
                    .headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .substringBefore(';')
                    .trim()
                    .equals("application/json", true)
        )
            throw MatrixResponseException()
        val body =
            try {
                json.readTree(response.body())
            } catch (_: tools.jackson.core.JacksonException) {
                throw MatrixResponseException()
            }
        if (body?.isObject != true) throw MatrixResponseException()
        return body
    }

    private fun text(body: JsonNode, key: String, limit: Int): String {
        val value = body.path(key)
        if (!value.isString) throw MatrixResponseException()
        return value.asString().also {
            if (it.length !in 1..limit || it.any { c -> c.code !in 33..126 })
                throw MatrixResponseException()
        }
    }
}
