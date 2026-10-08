package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.http.DomainFailureException
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import java.util.UUID
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping

class CompanyActorArgumentResolver(private val resolve: ResolveActor) :
    HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == Actor::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        container: ModelAndViewContainer?,
        request: NativeWebRequest,
        binder: WebDataBinderFactory?,
    ): Actor {
        val identity =
            SecurityContextHolder.getContext().authentication?.principal as? SessionIdentity
                ?: throw DomainFailureException(
                    Failure(FailureKind.UNAUTHENTICATED, "authentication_required")
                )
        val variables =
            request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, 0) as? Map<*, *>
        val companyId =
            variables?.get("companyId")?.toString()?.let {
                runCatching { UUID.fromString(it) }
                    .getOrElse {
                        throw DomainFailureException(
                            Failure(FailureKind.VALIDATION, "invalid_company_id")
                        )
                    }
            }
        val correlation =
            request.getAttribute("hris.correlationId", 0) as? UUID ?: UUID.randomUUID()
        return resolve
            .execute(identity.accountId, companyId, identity.authenticatedAt, correlation)
            .response()
    }
}
