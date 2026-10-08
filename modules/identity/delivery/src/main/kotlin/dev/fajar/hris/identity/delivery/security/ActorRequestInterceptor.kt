package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/** Resolves live identity once per request, including handlers without an Actor parameter. */
class ActorRequestInterceptor(private val resolve: ResolveActor) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val identity =
            SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedIdentity
                ?: throw DomainFailureException(
                    Failure(FailureKind.UNAUTHENTICATED, "authentication_required")
                )
        val method = handler as? HandlerMethod
        val transport =
            method?.getMethodAnnotation(SessionTransport::class.java)
                ?: method?.beanType?.getAnnotation(SessionTransport::class.java)
        if (
            (transport?.value == SessionKind.COOKIE && identity !is SessionIdentity) ||
                (transport?.value == SessionKind.NATIVE && identity !is NativeIdentity)
        )
            throw DomainFailureException(
                Failure(FailureKind.FORBIDDEN, "invalid_session_transport")
            )
        val variables =
            request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
        val companyId =
            variables?.get("companyId")?.toString()?.let { value ->
                runCatching { UUID.fromString(value) }
                    .getOrElse {
                        throw org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST
                        )
                    }
            }
        val correlation = request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID()
        val pendingAllowed =
            (handler as? HandlerMethod)?.hasMethodAnnotation(PendingMfaAllowed::class.java) == true
        val actor =
            resolve
                .execute(
                    identity.accountId,
                    companyId,
                    identity.authenticatedAt,
                    correlation,
                    identity.mfaVerifiedAt,
                    identity.credentialVersion,
                    !pendingAllowed,
                )
                .response()
        request.setAttribute("hris.actor", actor)
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        error: Exception?,
    ) {
        request.removeAttribute("hris.actor")
    }
}
