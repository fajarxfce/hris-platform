package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.DomainFailureException
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class CompanyActorArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == Actor::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        container: ModelAndViewContainer?,
        request: NativeWebRequest,
        binder: WebDataBinderFactory?,
    ): Actor =
        request.getAttribute("hris.actor", NativeWebRequest.SCOPE_REQUEST) as? Actor
            ?: throw DomainFailureException(
                Failure(FailureKind.UNAUTHENTICATED, "authentication_required")
            )
}
