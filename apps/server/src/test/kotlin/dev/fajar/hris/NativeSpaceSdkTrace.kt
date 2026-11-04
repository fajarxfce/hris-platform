package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** One response-loss fault after real admission/provider I/O; opaque digest-only observations. */
@Order(Ordered.HIGHEST_PRECEDENCE)
class NativeSpaceSdkTrace : OncePerRequestFilter() {
    private val json = JsonMapper.builder().build()
    private val owner = AtomicReference<String?>()
    private val lock = Any()
    private var first: List<String>? = null
    private var replay = false
    private var lost = false
    private var groupSyncs = 0
    private var targetEvent: String? = null
    private var encryptedTarget = false
    private val observed = mutableListOf<Map<String, Any>>()
    private val responses = mutableListOf<Map<String, Any>>()

    fun arm(token: String, eventId: String) =
        synchronized(lock) {
            check(owner.get() == null)
            check(token.startsWith("hris_mxg_a_"))
            owner.set(digest(token.toByteArray()))
            first = null
            replay = false
            lost = false
            groupSyncs = 0
            targetEvent = eventId
            encryptedTarget = false
            observed.clear()
            responses.clear()
        }

    fun reset() = synchronized(lock) { owner.set(null) }

    fun lost() = synchronized(lock) { lost }

    fun replayed() = synchronized(lock) { replay }

    fun hashes(): List<String> = synchronized(lock) { first?.toList().orEmpty() }

    fun syncs() = synchronized(lock) { groupSyncs }

    fun encryptedTarget() = synchronized(lock) { encryptedTarget }

    fun observation() =
        synchronized(lock) {
            mapOf(
                "lost" to lost,
                "replayed" to replay,
                "syncs" to groupSyncs,
                "encryptedTarget" to encryptedTarget,
                "hashes" to first.orEmpty(),
                "requests" to observed.toList(),
                "responses" to responses.toList(),
            )
        }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val token = request.getHeader("Authorization")?.removePrefix("Bearer ")
        if (owner.get() != null && request.requestURI.endsWith("/sync")) {
            synchronized(lock) {
                if (observed.size < 16)
                    observed.add(
                        mapOf(
                            "path" to request.requestURI,
                            "ownerMatches" to
                                (token != null && digest(token.toByteArray()) == owner.get()),
                            "groupCredential" to (token?.startsWith("hris_mxg_a_") == true),
                        )
                    )
            }
        }
        if (
            request.method != "GET" ||
                request.requestURI != "/_matrix/client/v3/sync" ||
                token == null ||
                digest(token.toByteArray()) != owner.get()
        ) {
            chain.doFilter(request, response)
            return
        }
        val captured = ContentCachingResponseWrapper(response)
        chain.doFilter(request, captured)
        if (captured.status == 200) {
            val bytes = captured.contentAsByteArray
            check(bytes.size <= 2_097_152)
            val tree = json.readTree(bytes)
            val frames = tree.path("to_device").path("events")
            synchronized(lock) {
                tree.path("rooms").path("join").properties().forEach { (_, room) ->
                    room.path("timeline").path("events").forEach { event ->
                        if (event.path("event_id").asString() == targetEvent) {
                            check(event.path("type").asString() == "m.room.encrypted")
                            encryptedTarget = true
                        }
                    }
                }
                if (responses.size < 16)
                    responses.add(
                        mapOf(
                            "toDeviceEvents" to frames.size(),
                            "joinedRooms" to tree.path("rooms").path("join").size(),
                            "invitedRooms" to tree.path("rooms").path("invite").size(),
                            "hasSince" to (request.getParameter("since") != null),
                        )
                    )
            }
            val hashes =
                frames
                    .iterator()
                    .asSequence()
                    .filter { it.path("type").asString() == "m.room.encrypted" }
                    .map { digest(canonical(it).toByteArray()) }
                    .sorted()
                    .toList()
            val drop =
                synchronized(lock) {
                    groupSyncs++
                    if (hashes.isNotEmpty() && first == null) {
                        check(hashes.size <= 64)
                        first = hashes
                        lost = true
                        true
                    } else {
                        if (hashes.isNotEmpty() && hashes == first) replay = true
                        false
                    }
                }
            if (drop) {
                captured.resetBuffer()
                captured.status = 503
                captured.contentType = "application/json"
                captured.writer.write(
                    "{\"errcode\":\"M_UNKNOWN\",\"error\":\"Owned response loss\"}"
                )
            }
        }
        captured.copyBodyToResponse()
    }

    private fun canonical(node: JsonNode): String =
        when {
            node.isObject ->
                node.propertyNames().toList().sorted().joinToString(",", "{", "}") {
                    json.writeValueAsString(it) + ":" + canonical(node.get(it))
                }
            node.isArray ->
                node.iterator().asSequence().joinToString(",", "[", "]", transform = ::canonical)
            else -> json.writeValueAsString(node)
        }

    private fun digest(value: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}

@TestConfiguration(proxyBeanMethods = false)
class NativeSpaceSdkTraceConfiguration {
    @Bean fun nativeSpaceSdkTrace() = NativeSpaceSdkTrace()
}
