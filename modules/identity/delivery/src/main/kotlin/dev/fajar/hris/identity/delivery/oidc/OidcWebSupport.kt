package dev.fajar.hris.identity.delivery.oidc

import dev.fajar.hris.identity.domain.usecases.SignInWithOidc
import java.time.Clock
import org.springframework.http.client.*
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.client.oidc.authentication.*
import org.springframework.security.oauth2.client.registration.*
import org.springframework.security.oauth2.client.web.*
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.client.*

class OidcWebSupport(
    settings: OidcClientSettings,
    signIn: SignInWithOidc,
    clock: Clock,
    contexts: HttpSessionSecurityContextRepository,
    transport: OidcHttpTransport,
) {
    private val requests = transport.requests
    private val limit = ClientHttpRequestInterceptor { request, body, execution ->
        val response = execution.execute(request, body)
        try {
            if (response.headers.contentLength > 1048576)
                throw java.io.IOException("OIDC response limit exceeded")
            BoundedOidcResponse(response)
        } catch (error: Exception) {
            response.close()
            throw error
        }
    }
    private val registration =
        ClientRegistration.withRegistrationId("company")
            .clientName("Company SSO")
            .clientId(settings.clientId)
            .clientSecret(settings.clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod(settings.authenticationMethod))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(settings.publicUrl + "/login/oauth2/code/company")
            .scope("openid")
            .issuerUri(settings.issuer)
            .authorizationUri(settings.authorizationUri)
            .tokenUri(settings.tokenUri)
            .jwkSetUri(settings.jwkSetUri)
            .userNameAttributeName("sub")
            .build()
    private val clients = InMemoryClientRegistrationRepository(registration)
    private val resolver =
        DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization").apply {
            setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
        }
    private val tokenClient =
        RestClientAuthorizationCodeTokenResponseClient().apply {
            setRestClient(
                RestClient.builder()
                    .requestFactory(requests)
                    .requestInterceptor(limit)
                    .configureMessageConverters {
                        it.addCustomConverter(FormHttpMessageConverter())
                        it.addCustomConverter(OAuth2AccessTokenResponseHttpMessageConverter())
                    }
                    .defaultStatusHandler(OAuth2ErrorResponseErrorHandler())
                    .build()
            )
        }
    private val decoder =
        NimbusJwtDecoder.withJwkSetUri(settings.jwkSetUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(RestTemplate(requests).apply { interceptors = listOf(limit) })
            .build()
            .apply {
                setJwtValidator(
                    JwtValidators.createDefaultWithValidators(OidcIdTokenValidator(registration))
                )
                setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter())
            }
    val decoderFactory =
        JwtDecoderFactory<ClientRegistration> { candidate ->
            require(candidate.registrationId == registration.registrationId)
            decoder
        }
    private val authorizations = ExpiringAuthorizationRequests(clock)
    private val users = ApplicationOidcUserService(signIn, clock)
    private val success = OidcSessionSuccessHandler(contexts)

    fun configure(security: HttpSecurity, json: tools.jackson.databind.ObjectMapper) {
        security.oauth2Login { oauth ->
            oauth.loginPage("/login")
            oauth.withObjectPostProcessor(
                object :
                    org.springframework.security.config.ObjectPostProcessor<
                        OAuth2LoginAuthenticationFilter
                    > {
                    override fun <O : OAuth2LoginAuthenticationFilter> postProcess(filter: O): O {
                        // Strip provider claims before any session repository can persist
                        // authentication.
                        filter.setAuthenticationDetailsSource { emptyMap<String, String>() }
                        filter.setAuthenticationResultConverter { result ->
                            val identity = (result.principal as ApplicationOidcUser).identity
                            org.springframework.security.oauth2.client.authentication
                                .OAuth2AuthenticationToken(
                                    identity,
                                    identity.authorities,
                                    result.clientRegistration.registrationId,
                                )
                        }
                        return filter
                    }
                }
            )
            oauth
                .clientRegistrationRepository(clients)
                .authorizedClientRepository(SignInOnlyAuthorizedClients())
            oauth.authorizationEndpoint {
                it.authorizationRequestResolver(resolver)
                    .authorizationRequestRepository(authorizations)
            }
            oauth.tokenEndpoint { it.accessTokenResponseClient(tokenClient) }
            oauth.userInfoEndpoint { it.oidcUserService(users) }
            oauth.successHandler(success)
            oauth.failureHandler { _, response, _ ->
                response.setHeader("Cache-Control", "no-store")
                response.status = 302
                response.setHeader("Location", "/login?sso=failed")
            }
        }
        security.addFilterBefore(
            OidcCallbackLimitFilter(json),
            OAuth2LoginAuthenticationFilter::class.java,
        )
    }
}
