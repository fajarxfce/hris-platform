package dev.fajar.hris.collaboration.data.matrix

import java.net.URLEncoder
import java.net.http.*
import java.nio.charset.StandardCharsets
import java.time.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Separate finite pool from control exchanges; no SQL, executor, retry queue or unowned future. */
class HttpMatrixSyncDataSource(
    private val client: HttpClient,
    private val configuration: MatrixProviderConfiguration,
    private val clock: Clock,
    private val maximumConcurrent: Int = 64,
) : MatrixSyncDataSource, AutoCloseable {
    private val active = mutableMapOf<Pair<String, String>, Connection>()
    private var closed = false
    private val json =
        JsonMapper.builder(
                JsonFactory.builder()
                    .streamReadConstraints(
                        StreamReadConstraints.builder()
                            .maxNestingDepth(64)
                            .maxStringLength(1_048_576)
                            .maxNumberLength(32)
                            .build()
                    )
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build()
            )
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

    init {
        require(maximumConcurrent in 1..256)
        require(client.followRedirects() == HttpClient.Redirect.NEVER)
        require(client.connectTimeout().orElse(Duration.ofSeconds(6)) <= Duration.ofSeconds(5))
    }

    override fun open(
        userId: String,
        deviceId: String,
        bearer: String,
        deadline: Instant,
    ): MatrixSyncConnection {
        require(userId.startsWith('@') && userId.length <= 255 && userId.all { it.code in 33..126 })
        require(deviceId.matches(Regex("HRIS_[a-f0-9]{32}")))
        require(bearer.length in 1..2048 && bearer.all { it.code in 33..126 })
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (clock.instant() >= deadline) throw MatrixDeadlineException()
        val key = userId to deviceId
        return synchronized(active) {
            if (closed || active.size >= maximumConcurrent || key in active)
                throw MatrixCapacityException()
            Connection(key, bearer, deadline).also { active[key] = it }
        }
    }

    override fun close() {
        val owned =
            synchronized(active) {
                closed = true
                active.values.toList()
            }
        owned.forEach { it.close() }
    }

    private inner class Connection(
        private val key: Pair<String, String>,
        private val bearer: String,
        private val deadline: Instant,
    ) : MatrixSyncConnection {
        private val disposed = AtomicBoolean()
        private val inFlight = AtomicBoolean()
        private val pending = AtomicReference<CompletableFuture<*>?>()

        override fun registerFilter(filter: Map<String, Any>): String {
            val node =
                request(
                    "/_matrix/client/v3/user/${encode(key.first)}/filter",
                    filter,
                    configuration.requestTimeout,
                    65536,
                )
            return text(node, "filter_id", 128)
        }

        override fun sync(
            since: String?,
            filterId: String,
            timeoutMillis: Int,
            fullState: Boolean,
        ): JsonNode {
            require(timeoutMillis in 0..30_000)
            require(filterId.length in 1..128 && filterId.all { it.code in 33..126 })
            require(since == null || (since.length in 1..1024 && since.all { it.code in 33..126 }))
            val left = remaining()
            // Leave provider response/parse time within the captured admission, never extend it.
            val poll = minOf(timeoutMillis.toLong(), (left.toMillis() - 2000).coerceAtLeast(0))
            val path =
                "/_matrix/client/v3/sync?filter=${encode(filterId)}&timeout=$poll&full_state=$fullState&set_presence=offline" +
                    (since?.let { "&since=${encode(it)}" } ?: "")
            return request(path, null, minOf(left, Duration.ofMillis(poll + 5000)), 8_388_608)
        }

        override fun roomState(roomId: String): JsonNode {
            require(
                roomId.startsWith('!') &&
                    roomId.length in 2..255 &&
                    roomId.all { it.code in 33..126 }
            )
            return request(
                "/_matrix/client/v3/rooms/${encode(roomId)}/state",
                null,
                configuration.requestTimeout,
                1_048_576,
            )
        }

        private fun remaining(): Duration {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (disposed.get()) throw java.util.concurrent.CancellationException()
            return Duration.between(clock.instant(), deadline).also {
                if (it <= Duration.ZERO) throw MatrixDeadlineException()
            }
        }

        private fun request(
            path: String,
            body: Map<String, Any>?,
            timeout: Duration,
            maximumBytes: Int,
        ): JsonNode {
            if (!inFlight.compareAndSet(false, true)) throw MatrixCapacityException()
            return try {
                performRequest(path, body, timeout, maximumBytes)
            } finally {
                inFlight.set(false)
            }
        }

        private fun performRequest(
            path: String,
            body: Map<String, Any>?,
            timeout: Duration,
            maximumBytes: Int,
        ): JsonNode {
            val bytes = body?.let(json::writeValueAsBytes)
            if (bytes != null && bytes.size > 65_536) throw MatrixResponseException()
            val budget = minOf(timeout, remaining())
            val builder =
                HttpRequest.newBuilder(configuration.origin.resolve(path))
                    .timeout(budget)
                    .header("Authorization", "Bearer $bearer")
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "identity")
            if (bytes == null) builder.GET()
            else
                builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
            val future =
                client.sendAsync(
                    builder.build(),
                    HttpResponse.BodyHandler { BoundedMatrixBodySubscriber(maximumBytes) },
                )
            if (!pending.compareAndSet(null, future)) {
                future.cancel(true)
                throw MatrixCapacityException()
            }
            try {
                if (disposed.get()) {
                    future.cancel(true)
                    throw java.util.concurrent.CancellationException()
                }
                val response = future.get(budget.toNanos(), TimeUnit.NANOSECONDS)
                remaining()
                if (response.statusCode() != 200) throw MatrixStatusException(response.statusCode())
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
                val result =
                    try {
                        json.readTree(response.body())
                    } catch (_: tools.jackson.core.JacksonException) {
                        throw MatrixResponseException()
                    }
                remaining()
                return result ?: throw MatrixResponseException()
            } finally {
                if (!future.isDone) future.cancel(true)
                pending.compareAndSet(future, null)
            }
        }

        override fun close() {
            if (!disposed.compareAndSet(false, true)) return
            pending.get()?.cancel(true)
            synchronized(active) { active.remove(key, this) }
        }
    }

    private fun encode(value: String) =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun text(node: JsonNode, name: String, limit: Int): String {
        val value = node.path(name)
        if (
            !node.isObject ||
                !value.isString ||
                value.asString().length !in 1..limit ||
                value.asString().any { it.code !in 33..126 }
        )
            throw MatrixResponseException()
        return value.asString()
    }
}
