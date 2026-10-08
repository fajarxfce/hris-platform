package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.usecases.ResolveNativeAccess
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

/** Runs only in the stateless security chain. Invalid credentials never fall back to cookies. */
class NativeAccessFilter(private val resolve: ResolveNativeAccess, json: ObjectMapper) :
    OncePerRequestFilter() {
    private val failures = ApiAuthenticationFailureHandler(json)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val headers = java.util.Collections.list(request.getHeaders("Authorization"))
        if (headers.isEmpty()) {
            chain.doFilter(request, response)
            return
        }
        val header = headers.singleOrNull()
        val result =
            if (
                header != null &&
                    header.startsWith("Bearer ", ignoreCase = true) &&
                    header.length <= 100
            ) {
                resolve.execute(header.substring(7))
            } else Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "native_session_invalid"))
        if (result is Result.Failed) {
            failures.onAuthenticationFailure(
                request,
                response,
                IdentityAuthenticationException(result.failure),
            )
            return
        }
        val access = (result as Result.Success).value
        val identity =
            NativeIdentity(
                access.sessionId,
                access.sessionVersion,
                access.accountId,
                access.authenticatedAt,
                access.credentialVersion,
                access.mfaVerifiedAt,
            )
        val context =
            SecurityContextHolder.createEmptyContext().apply {
                authentication =
                    UsernamePasswordAuthenticationToken.authenticated(identity, null, emptyList())
            }
        SecurityContextHolder.setContext(context)
        response.setHeader("Cache-Control", "no-store")
        try {
            chain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
