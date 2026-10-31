package dev.fajar.hris.collaboration.data.matrix

import java.net.URLEncoder
import java.net.http.*
import java.nio.charset.StandardCharsets
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
) : MatrixClientDataSource, MatrixRoomDataSource, MatrixKeyDataSource {
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

    override fun queryDevices(
        devices: Map<String, List<String>>,
        token: String,
        deadline: Instant,
    ): JsonNode {
        require(devices.size in 1..100)
        require(
            devices.all { (user, ids) ->
                user.length in 1..255 &&
                    user.all { it.code in 33..126 } &&
                    ids.size in 1..5 &&
                    ids.all { id -> id.length in 1..128 && id.all { it.code in 33..126 } }
            }
        )
        val response =
            roomRequest(
                "/_matrix/client/v3/keys/query",
                "POST",
                mapOf("device_keys" to devices, "timeout" to 1000),
                token,
                deadline,
                maximumResponseBytes = 1_048_576,
            )
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        return parse(response)
    }

    override fun queryDeviceKeys(
        userId: String,
        deviceId: String,
        token: String,
        deadline: Instant,
    ): JsonNode {
        require(userId.length in 1..255 && userId.all { it.code in 33..126 })
        require(deviceId.length in 1..128 && deviceId.all { it.code in 33..126 })
        val response =
            roomRequest(
                "/_matrix/client/v3/keys/query",
                "POST",
                mapOf("device_keys" to mapOf(userId to listOf(deviceId)), "timeout" to 1000),
                token,
                deadline,
            )
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        return parse(response)
    }

    override fun uploadKeys(body: Map<String, Any>, token: String, deadline: Instant): JsonNode {
        val response = roomRequest("/_matrix/client/v3/keys/upload", "POST", body, token, deadline)
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        return parse(response)
    }

    override fun resolveAlias(alias: String, token: String, deadline: Instant): String? {
        require(alias.startsWith("#") && alias.length in 2..255)
        val response =
            roomRequest(
                "/_matrix/client/v3/directory/room/${segment(alias)}",
                "GET",
                null,
                token,
                deadline,
            )
        val body = parse(response)
        if (response.statusCode() == 404 && body.path("errcode").asString() == "M_NOT_FOUND")
            return null
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        return roomId(body)
    }

    override fun createRoom(
        body: Map<String, Any>,
        token: String,
        deadline: Instant,
    ): MatrixRoomCreateData {
        val response = roomRequest("/_matrix/client/v3/createRoom", "POST", body, token, deadline)
        val value = parse(response)
        if (response.statusCode() == 400 && value.path("errcode").asString() == "M_ROOM_IN_USE")
            return MatrixRoomCreateData.AliasInUse
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        return MatrixRoomCreateData.Created(roomId(value))
    }

    override fun roomState(
        roomId: String,
        token: String,
        deadline: Instant,
    ): List<MatrixRoomStateEventData> {
        val response = roomRequest("${roomPath(roomId)}/state", "GET", null, token, deadline)
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        val value = parseJson(response)
        if (!value.isArray || value.size() > 256) throw MatrixResponseException()
        val events =
            value
                .iterator()
                .asSequence()
                .map { event ->
                    if (!event.isObject) throw MatrixResponseException()
                    val type = text(event, "type", 255)
                    val key = event.path("state_key")
                    val content = event.path("content")
                    if (
                        !key.isString ||
                            key.asString().length > 255 ||
                            key.asString().any(Char::isISOControl) ||
                            !content.isObject
                    )
                        throw MatrixResponseException()
                    MatrixRoomStateEventData(
                        type,
                        key.asString(),
                        text(event, "sender", 255),
                        content,
                    )
                }
                .toList()
        if (events.distinctBy { it.type to it.stateKey }.size != events.size)
            throw MatrixResponseException()
        return events
    }

    override fun putRoomState(
        roomId: String,
        type: MatrixRoomStateType,
        content: Map<String, Any>,
        token: String,
        deadline: Instant,
    ) {
        val response =
            roomRequest(
                "${roomPath(roomId)}/state/${type.eventType}",
                "PUT",
                content,
                token,
                deadline,
            )
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        val event = text(parse(response), "event_id", 255)
        if (!event.startsWith("$")) throw MatrixResponseException()
    }

    override fun invite(roomId: String, userId: String, token: String, deadline: Instant) {
        require(
            userId.startsWith("@") && userId.length in 2..255 && userId.all { it.code in 33..126 }
        )
        val response =
            roomRequest(
                "${roomPath(roomId)}/invite",
                "POST",
                mapOf("user_id" to userId),
                token,
                deadline,
            )
        if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
        parse(response)
    }

    private fun roomPath(roomId: String): String {
        require(roomId.startsWith("!") && roomId.length in 2..255)
        return "/_matrix/client/v3/rooms/${segment(roomId)}"
    }

    private fun roomId(body: JsonNode): String =
        text(body, "room_id", 255).also {
            // Room version 12 uses hash-based IDs; do not require a server suffix.
            if (!it.startsWith("!")) throw MatrixResponseException()
        }

    private fun segment(value: String): String {
        require(value.all { it.code in 33..126 })
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }

    private fun roomRequest(
        path: String,
        method: String,
        body: Map<String, Any>?,
        token: String,
        deadline: Instant,
        maximumResponseBytes: Int = 65536,
    ): HttpResponse<ByteArray> {
        require(token.length in 1..2048 && token.all { it.code in 33..126 })
        val remaining = Duration.between(clock.instant(), deadline)
        if (remaining <= Duration.ZERO) throw MatrixDeadlineException()
        val bytes = body?.let(json::writeValueAsBytes)
        if (bytes != null && bytes.size > 65536) throw MatrixResponseException()
        val afterSerialization = Duration.between(clock.instant(), deadline)
        if (afterSerialization <= Duration.ZERO) throw MatrixDeadlineException()
        val timeout = minOf(configuration.requestTimeout, remaining, afterSerialization)
        val builder =
            HttpRequest.newBuilder(configuration.origin.resolve(path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("Authorization", "Bearer $token")
        if (bytes != null) builder.header("Content-Type", "application/json")
        return exchange(
            builder
                .method(
                    method,
                    if (bytes == null) HttpRequest.BodyPublishers.noBody()
                    else HttpRequest.BodyPublishers.ofByteArray(bytes),
                )
                .build(),
            timeout,
            maximumResponseBytes,
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

    private fun exchange(
        request: HttpRequest,
        timeout: Duration,
        maximumResponseBytes: Int = 65536,
    ): HttpResponse<ByteArray> {
        if (!exchanges.tryAcquire()) throw MatrixCapacityException()
        return try {
            val pending =
                client.sendAsync(
                    request,
                    HttpResponse.BodyHandler { BoundedMatrixBodySubscriber(maximumResponseBytes) },
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

    private fun parse(response: HttpResponse<ByteArray>): JsonNode =
        parseJson(response).also { if (!it.isObject) throw MatrixResponseException() }

    private fun parseJson(response: HttpResponse<ByteArray>): JsonNode {
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
        return body ?: throw MatrixResponseException()
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
