package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.usecases.SignInWithPassword
import java.time.Clock
import java.util.UUID
import org.slf4j.MDC
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

class AccountAuthenticationProvider(
    private val signIn: SignInWithPassword,
    private val clock: Clock,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication {
        val correlationId = MDC.get("correlationId")?.let(UUID::fromString) ?: UUID.randomUUID()
        val origin = (authentication.details as? LoginOrigin)?.address ?: "unspecified"
        // ProviderManager copies details into the result. Keep request-only data
        // out of the persisted session and its serialization graph.
        (authentication as UsernamePasswordAuthenticationToken).details = null
        return when (
            val result =
                signIn.execute(
                    authentication.name,
                    authentication.credentials.toString(),
                    correlationId,
                    origin,
                )
        ) {
            is Result.Failed -> throw IdentityAuthenticationException(result.failure)
            is Result.Success ->
                UsernamePasswordAuthenticationToken.authenticated(
                    SessionIdentity(result.value.id, clock.instant(), result.value.mfaConfigured),
                    null,
                    listOf(SimpleGrantedAuthority("SESSION")),
                )
        }
    }

    override fun supports(type: Class<*>): Boolean =
        UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(type)
}
