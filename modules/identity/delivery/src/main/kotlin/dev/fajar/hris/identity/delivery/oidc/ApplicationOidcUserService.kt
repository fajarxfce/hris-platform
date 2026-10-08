package dev.fajar.hris.identity.delivery.oidc

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.delivery.security.SessionIdentity
import dev.fajar.hris.identity.domain.usecases.SignInWithOidc
import java.time.Clock
import java.util.UUID
import org.slf4j.MDC
import org.springframework.security.oauth2.client.oidc.userinfo.*
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.core.oidc.user.OidcUser

class ApplicationOidcUserService(private val signIn: SignInWithOidc, private val clock: Clock) :
    OAuth2UserService<OidcUserRequest, OidcUser> {
    private val delegate = OidcUserService().apply { setRetrieveUserInfo { false } }

    override fun loadUser(request: OidcUserRequest): OidcUser {
        val verified = delegate.loadUser(request)
        val issuer =
            verified.issuer?.toExternalForm()
                ?: throw OAuth2AuthenticationException(OAuth2Error("sso_sign_in_failed"))
        val subject =
            verified.subject
                ?: throw OAuth2AuthenticationException(OAuth2Error("sso_sign_in_failed"))
        val correlation = MDC.get("correlationId")?.let(UUID::fromString) ?: UUID.randomUUID()
        return when (val result = signIn.execute(issuer, subject, correlation)) {
            is Result.Failed ->
                throw OAuth2AuthenticationException(OAuth2Error("sso_sign_in_failed"))
            is Result.Success ->
                ApplicationOidcUser(
                    SessionIdentity(
                        result.value.id,
                        clock.instant(),
                        result.value.mfaConfigured,
                        result.value.securityVersion,
                    ),
                    verified,
                )
        }
    }
}
