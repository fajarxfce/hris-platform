package dev.fajar.hris

import org.springframework.boot.test.context.TestComponent
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/** Retain only an application error identifier from the owned gateway response. */
@TestComponent
@ControllerAdvice
class NativeSdkErrorTrace : ResponseBodyAdvice<Any> {
    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ) = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        val servlet = (request as? ServletServerHttpRequest)?.servletRequest
        if (servlet?.requestURI?.startsWith("/_matrix/") == true) {
            val code = (body as? Map<*, *>)?.get("org.hris.error_code") as? String
            if (code?.matches(Regex("[a-z_]{1,100}")) == true)
                servlet.setAttribute("fixture.matrix.error", code)
        }
        return body
    }
}
