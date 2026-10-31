package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

/** Bounded, code-only observations: never credentials, IDs, cursor/filter values or bodies. */
class NativeSdkGatewayTrace : OncePerRequestFilter() {
    private val observations = mutableListOf<Map<String, Any>>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val keyRequest =
            if (request.requestURI == "/_matrix/client/v3/keys/upload")
                ContentCachingRequestWrapper(request, 65_536)
            else null
        try {
            chain.doFilter(keyRequest ?: request, response)
        } finally {
            if (request.requestURI.startsWith("/_matrix/"))
                synchronized(observations) {
                    if (observations.size < 512)
                        observations.add(
                            mapOf(
                                "method" to
                                    request.method
                                        .takeIf { it in setOf("GET", "POST", "PUT", "DELETE") }
                                        .orEmpty(),
                                "route" to
                                    request.requestURI.split('/').joinToString("/") {
                                        if (it in segments) it else "_"
                                    },
                                "query" to
                                    request.parameterMap.keys.filter { it in parameters }.sorted(),
                                "unknownQuery" to
                                    request.parameterMap.keys.any { it !in parameters },
                                "status" to response.status,
                                "contentLength" to request.contentLengthLong,
                                "transferEncoding" to
                                    (request.getHeader("Transfer-Encoding") != null),
                                "timeoutMillis" to
                                    (request.getParameter("timeout")?.takeIf {
                                        it.matches(Regex("[0-9]{1,9}"))
                                    } ?: "absent_or_invalid"),
                                "errorCode" to (request.getAttribute("fixture.matrix.error") ?: ""),
                            ) +
                                (keyRequest?.let {
                                    mapOf("keyShape" to nativeSdkKeyShape(it.contentAsByteArray))
                                } ?: emptyMap())
                        )
                }
        }
    }

    fun snapshot(): List<Map<String, Any>> = synchronized(observations) { observations.toList() }

    companion object {
        private val segments =
            setOf(
                "",
                "_matrix",
                "client",
                "v3",
                "r0",
                "unstable",
                "versions",
                "capabilities",
                "sync",
                "keys",
                "upload",
                "query",
                "claim",
                "rooms",
                "join",
                "state",
                "members",
                "messages",
                "context",
                "event",
                "send",
                "sendToDevice",
                "m.room.encrypted",
                "pushrules",
                "room_keys",
                "version",
                "user",
                "filter",
                "account_data",
                "profile",
                "displayname",
                "avatar_url",
                "whoami",
                "account",
                "turnServer",
                "voip",
            )
        private val parameters =
            setOf(
                "since",
                "timeout",
                "full_state",
                "set_presence",
                "use_state_after",
                "filter",
                "at",
                "membership",
                "not_membership",
                "from",
                "to",
                "dir",
                "limit",
            )
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NativeSdkGatewayTraceConfiguration {
    @Bean fun nativeSdkGatewayTrace() = NativeSdkGatewayTrace()

    @Bean fun nativeSdkErrorTrace() = NativeSdkErrorTrace()
}
