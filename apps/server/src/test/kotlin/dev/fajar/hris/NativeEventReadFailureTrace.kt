package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter

/** Passive fixture evidence only: no bodies, credentials, event IDs or raw paths. */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class NativeEventReadFailureTrace : OncePerRequestFilter() {
    private val failures = mutableListOf<Map<String, Any>>()
    private val startupFailures = mutableListOf<Map<String, Any>>()
    private var active = false

    @Synchronized
    fun arm() {
        check(!active)
        active = true
    }

    @Synchronized fun snapshot(): List<Map<String, Any>> = failures.toList()

    @Synchronized fun startupSnapshot(): List<Map<String, Any>> = startupFailures.toList()

    @Synchronized
    private fun record(request: HttpServletRequest, status: Int) {
        val captured = if (active) failures else startupFailures
        check(captured.size < 32)
        val path = request.requestURI
        val operation =
            when {
                path.endsWith("/login") -> "login"
                path.contains("/context/") -> "context"
                path.contains("/event/") -> "event"
                path.endsWith("/messages") -> "messages"
                path.endsWith("/sync") -> "sync"
                else -> "other"
            }
        captured +=
            mapOf(
                "operation" to operation,
                "status" to status,
                "code" to (request.getAttribute("fixture.matrix.error") as? String ?: "none"),
            )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        chain.doFilter(request, response)
        if (
            request.requestURI.startsWith("/_matrix/") &&
                (response.status == 429 || response.status >= 500)
        )
            record(request, response.status)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NativeEventReadFailureTraceConfiguration {
    @Bean fun eventReadFailureTrace() = NativeEventReadFailureTrace()
}
