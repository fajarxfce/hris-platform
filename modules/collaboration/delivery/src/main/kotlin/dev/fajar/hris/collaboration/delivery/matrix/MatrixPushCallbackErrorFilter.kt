package dev.fajar.hris.collaboration.delivery.matrix

import dev.fajar.hris.collaboration.delivery.di.MatrixPushCallbackSettings
import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.SocketTimeoutException

/** Tomcat marks socket read failures for ERROR dispatch even if the controller catches them. */
class MatrixPushCallbackErrorFilter(private val settings: MatrixPushCallbackSettings) : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (
            request !is HttpServletRequest ||
                response !is HttpServletResponse ||
                request.dispatcherType != DispatcherType.ERROR ||
                request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) !=
                    "/_matrix/push/v1/notify"
        ) {
            chain.doFilter(request, response)
            return
        }
        // Only server-populated dispatch attributes are inspected; no exception text is rendered.
        var cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as? Throwable
        var timeout = false
        repeat(8) {
            if (cause is SocketTimeoutException) timeout = true
            cause = cause?.cause
        }
        val status =
            when {
                settings.sources?.allows(request.remoteAddr) != true -> 403
                timeout -> 408
                request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) == 400 -> 400
                else -> 503
            }
        response.status = status
        response.contentType = "application/json"
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("Connection", "close")
        response.outputStream.write(
            "{\"errcode\":\"M_UNKNOWN\",\"error\":\"The push callback could not be accepted.\"}"
                .toByteArray(Charsets.UTF_8)
        )
    }
}
