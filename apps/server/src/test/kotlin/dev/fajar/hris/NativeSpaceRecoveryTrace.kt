package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter

/** Isolated test gateway counters only; never retain credentials, paths, IDs or request bodies. */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class NativeSpaceRecoveryTrace : OncePerRequestFilter() {
    private val counts = mutableMapOf<String, Int>()
    private var active = false

    @Synchronized
    fun arm() {
        check(!active)
        active = true
    }

    @Synchronized fun snapshot(): Map<String, Int> = counts.toMap()

    @Synchronized
    private fun record(name: String) {
        if (active) counts[name] = minOf(10_000, counts.getOrDefault(name, 0) + 1)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (!request.requestURI.startsWith("/_matrix/")) {
            chain.doFilter(request, response)
            return
        }
        record("requests")
        when {
            request.requestURI.endsWith("/sync") -> record("sync")
            request.requestURI == "/_matrix/client/unstable/dev.fajar.hris/pusher" ->
                record("managedPusher")
            request.requestURI.contains("/account_data/m.secret_storage.") ->
                record("secretStorage")
            request.requestURI.contains("/send/m.room.encrypted/") -> record("encryptedSend")
        }
        chain.doFilter(request, response)
        when {
            response.status == 401 || response.status == 403 -> record("rejected")
            response.status >= 500 -> record("serverError")
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NativeSpaceRecoveryTraceConfiguration {
    @Bean fun spaceRecoveryTrace() = NativeSpaceRecoveryTrace()
}
