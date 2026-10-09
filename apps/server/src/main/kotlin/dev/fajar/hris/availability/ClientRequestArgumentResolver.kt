package dev.fajar.hris.availability

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.DomainFailureException
import dev.fajar.hris.identity.delivery.security.AuthenticatedIdentity
import dev.fajar.hris.identity.delivery.security.NativeIdentity
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class ClientRequestArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter) =
        parameter.parameterType == ClientRequest::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        container: ModelAndViewContainer?,
        request: NativeWebRequest,
        binder: WebDataBinderFactory?,
    ): ClientRequest {
        val principal =
            SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedIdentity
                ?: throw DomainFailureException(
                    Failure(FailureKind.UNAUTHENTICATED, "authentication_required")
                )
        val servlet = requireNotNull(request.getNativeRequest(HttpServletRequest::class.java))
        return ClientRequest(readClientVersion(servlet), principal is NativeIdentity)
    }
}
