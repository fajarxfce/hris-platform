package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Counts only the isolated proof host's native requests. No tokens, IDs, bodies or URI retention.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class NativeOriginalDeliveryTrace : OncePerRequestFilter() {
    private val counts = mutableMapOf<String, Int>()
    private var armed = false

    @Synchronized
    fun arm() {
        check(!armed)
        armed = true
    }

    @Synchronized fun snapshot(): Map<String, Int> = counts.toMap()

    @Synchronized
    private fun record(key: String) {
        if (armed) counts[key] = minOf(10_000, counts.getOrDefault(key, 0) + 1)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val path = request.requestURI
        if (
            request.getHeader("X-HRIS-Sender-Proof") == "1" &&
                path.startsWith("/api/v1/collaboration/spaces/")
        ) {
            record("requests")
            when {
                request.method == "GET" && path.endsWith("/transport") -> record("directTransport")
                request.method == "POST" && path.endsWith("/native-device/bootstrap") ->
                    record("bootstrapReplay")
                request.method == "POST" && path.endsWith("/native-device") ->
                    record("registration")
                request.method == "POST" &&
                    (path.endsWith("/room") || path.endsWith("/room/requests")) ->
                    record("roomRequest")
            }
            chain.doFilter(request, response)
            if (response.status >= 400) record("failed")
        } else chain.doFilter(request, response)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NativeOriginalDeliveryTraceConfiguration {
    @Bean fun originalDeliveryTrace() = NativeOriginalDeliveryTrace()
}
