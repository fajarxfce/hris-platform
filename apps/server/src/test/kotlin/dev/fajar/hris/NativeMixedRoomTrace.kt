package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UriUtils

/** Read-only, bounded observations of real gateway outcomes for two owned fictional rooms. */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class NativeMixedRoomTrace : OncePerRequestFilter() {
    private var rooms = emptyMap<String, String>()
    private var started = 0L
    private val observations = mutableListOf<Map<String, Any>>()

    @Synchronized
    fun track(unavailable: String, ready: String) {
        check(rooms.isEmpty() && unavailable != ready)
        rooms = mapOf(unavailable to "unavailable", ready to "ready")
        started = System.nanoTime()
    }

    @Synchronized fun snapshot() = observations.map { it.toMap() }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val label =
            if (request.method == "PUT" && request.requestURI.matches(SEND)) {
                val room = UriUtils.decode(request.requestURI.split('/')[5], StandardCharsets.UTF_8)
                synchronized(this) { rooms[room] }
            } else null
        val start = System.nanoTime()
        try {
            chain.doFilter(request, response)
        } finally {
            if (label != null)
                synchronized(this) {
                    check(observations.size < 64) { "Owned mixed-room request bound exceeded" }
                    observations +=
                        mapOf(
                            "room" to label,
                            "startedMillis" to (start - started) / 1_000_000,
                            "finishedMillis" to (System.nanoTime() - started) / 1_000_000,
                            "status" to response.status,
                            "errorCode" to
                                ((request.getAttribute("fixture.matrix.error") as? String)?.takeIf {
                                    it.matches(Regex("[a-z_]{1,100}"))
                                } ?: ""),
                        )
                }
        }
    }

    private companion object {
        val SEND = Regex("/_matrix/client/v3/rooms/[^/]+/send/m\\.room\\.encrypted/[^/]+")
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NativeMixedRoomTraceConfiguration {
    @Bean fun nativeMixedRoomTrace() = NativeMixedRoomTrace()
}
