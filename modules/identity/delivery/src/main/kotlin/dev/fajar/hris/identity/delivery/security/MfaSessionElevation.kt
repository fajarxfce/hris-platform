package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.DomainFailureException
import dev.fajar.hris.identity.domain.entities.MfaProof
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.session.*
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.*

class MfaSessionElevation(
    private val contexts: HttpSessionSecurityContextRepository,
    csrf: HttpSessionCsrfTokenRepository,
) {
    private val sessions =
        CompositeSessionAuthenticationStrategy(
            listOf(ChangeSessionIdAuthenticationStrategy(), CsrfAuthenticationStrategy(csrf))
        )

    fun elevate(proof: MfaProof, request: HttpServletRequest, response: HttpServletResponse) {
        val current = SecurityContextHolder.getContext().authentication
        val principal = current?.principal as? SessionIdentity
        if (principal?.accountId != proof.accountId)
            throw DomainFailureException(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
        val identity =
            principal.copy(
                authenticatedAt = proof.verifiedAt,
                mfaConfigured = true,
                credentialVersion = proof.securityVersion,
                mfaVerifiedAt = proof.verifiedAt,
            )
        val authenticated =
            UsernamePasswordAuthenticationToken.authenticated(identity, null, current.authorities)
        sessions.onAuthentication(authenticated, request, response)
        val context =
            SecurityContextHolder.createEmptyContext().apply { authentication = authenticated }
        SecurityContextHolder.setContext(context)
        contexts.saveContext(context, request, response)
        response.setHeader("Cache-Control", "no-store")
    }
}
