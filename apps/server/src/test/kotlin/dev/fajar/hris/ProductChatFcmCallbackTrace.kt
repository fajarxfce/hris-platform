package dev.fajar.hris

import dev.fajar.hris.collaboration.delivery.matrix.MATRIX_PUSH_MAXIMUM_BYTES
import dev.fajar.hris.collaboration.delivery.matrix.matrixPushNotification
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

/** Captures only notification shape/equality, after the actual callback has consumed its body. */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ProductChatFcmCallbackTrace : OncePerRequestFilter() {
    private val observations = mutableListOf<Map<String, Any>>()
    @Volatile private var room: String? = null

    fun expectRoom(value: String) {
        require(value.startsWith('!') && value.length <= 255)
        check(room == null)
        room = value
    }

    override fun shouldNotFilter(request: HttpServletRequest) =
        request.method != "POST" || request.requestURI != "/_matrix/push/v1/notify"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val captured = ContentCachingRequestWrapper(request, MATRIX_PUSH_MAXIMUM_BYTES + 1)
        try {
            chain.doFilter(captured, response)
        } finally {
            val hint = matrixPushNotification(captured.contentAsByteArray)
            synchronized(observations) {
                if (observations.size < 16)
                    observations.add(
                        mapOf(
                            "status" to response.status,
                            "parsed" to (hint != null),
                            "devices" to (hint?.keys?.size ?: 0),
                            "hasEvent" to (hint?.event != null),
                            "expectedRoom" to (hint?.event?.roomId?.let { it == room } ?: false),
                        )
                    )
            }
        }
    }

    fun snapshot() = synchronized(observations) { observations.toList() }
}

@TestConfiguration(proxyBeanMethods = false)
class ProductChatFcmCallbackTraceConfiguration {
    @Bean fun productChatFcmCallbackTrace() = ProductChatFcmCallbackTrace()
}
