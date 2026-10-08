package dev.fajar.hris.identity.delivery.di

import dev.fajar.hris.identity.delivery.security.*
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import dev.fajar.hris.identity.domain.usecases.SignInWithPassword
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class IdentityWebConfiguration {
    @Bean fun sessionContexts() = HttpSessionSecurityContextRepository()

    @Bean fun csrfTokens() = HttpSessionCsrfTokenRepository()

    @Bean
    fun mfaSessionElevation(
        contexts: HttpSessionSecurityContextRepository,
        csrf: HttpSessionCsrfTokenRepository,
    ) = MfaSessionElevation(contexts, csrf)

    @Bean
    fun passwordAuthenticationProvider(
        signIn: SignInWithPassword,
        clock: Clock,
    ): org.springframework.security.authentication.AuthenticationProvider =
        AccountAuthenticationProvider(signIn, clock)

    @Bean
    fun actorArguments(resolve: ResolveActor): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addInterceptors(
                registry: org.springframework.web.servlet.config.annotation.InterceptorRegistry
            ) {
                registry
                    .addInterceptor(ActorRequestInterceptor(resolve))
                    .addPathPatterns("/api/v1/**")
                    .excludePathPatterns("/api/v1/auth/csrf", "/api/v1/auth/native/refresh")
            }

            override fun addArgumentResolvers(
                resolvers: MutableList<HandlerMethodArgumentResolver>
            ) {
                resolvers.add(CompanyActorArgumentResolver())
            }
        }

    @Bean
    @org.springframework.core.annotation.Order(1)
    fun nativeSecurity(
        http: HttpSecurity,
        resolve: dev.fajar.hris.identity.domain.usecases.ResolveNativeAccess,
        json: ObjectMapper,
    ): SecurityFilterChain {
        http.securityMatcher(
            org.springframework.security.web.util.matcher.RequestMatcher { request ->
                request.getHeader("Authorization") != null ||
                    request.servletPath == "/api/v1/auth/native/refresh"
            }
        )
        http.sessionManagement {
            it.sessionCreationPolicy(
                org.springframework.security.config.http.SessionCreationPolicy.STATELESS
            )
        }
        http.securityContext {
            it.securityContextRepository(
                org.springframework.security.web.context.NullSecurityContextRepository()
            )
        }
        http.csrf { it.disable() }
        http.requestCache { it.disable() }
        http.logout { it.disable() }
        http.formLogin { it.disable() }
        http.httpBasic { it.disable() }
        http.authorizeHttpRequests {
            it.requestMatchers("/api/v1/auth/native/refresh")
                .permitAll()
                .anyRequest()
                .authenticated()
        }
        http.exceptionHandling {
            it.authenticationEntryPoint { request, response, error ->
                ApiAuthenticationFailureHandler(json)
                    .onAuthenticationFailure(request, response, error)
            }
        }
        http.addFilterBefore(
            NativeAccessFilter(resolve, json),
            org.springframework.security.web.access.intercept.AuthorizationFilter::class.java,
        )
        return http.build()
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    fun webSecurity(
        http: HttpSecurity,
        provider: org.springframework.security.authentication.AuthenticationProvider,
        json: ObjectMapper,
        contexts: HttpSessionSecurityContextRepository,
        csrf: HttpSessionCsrfTokenRepository,
    ): SecurityFilterChain {
        val authentication =
            JsonLoginFilter(ProviderManager(provider), JsonLoginConverter(json)).apply {
                setSecurityContextRepository(contexts)
                setSessionAuthenticationStrategy(
                    CompositeSessionAuthenticationStrategy(
                        listOf(
                            ChangeSessionIdAuthenticationStrategy(),
                            CsrfAuthenticationStrategy(csrf),
                        )
                    )
                )
                setAuthenticationSuccessHandler(ApiAuthenticationSuccessHandler(json))
                setAuthenticationFailureHandler(ApiAuthenticationFailureHandler(json))
            }
        http.csrf { it.csrfTokenRepository(csrf) }
        http.securityContext { it.securityContextRepository(contexts).requireExplicitSave(true) }
        http.authorizeHttpRequests {
            it.requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf", "/actuator/health/**")
                .permitAll()
                .anyRequest()
                .authenticated()
        }
        http.exceptionHandling {
            it.authenticationEntryPoint { request, response, error ->
                ApiAuthenticationFailureHandler(json)
                    .onAuthenticationFailure(request, response, error)
            }
            it.accessDeniedHandler { request, response, _ ->
                response.status = 403
                response.contentType = "application/problem+json"
                json.writeValue(
                    response.outputStream,
                    mapOf(
                        "status" to 403,
                        "code" to "access_denied",
                        "correlationId" to request.getAttribute("hris.correlationId")?.toString(),
                    ),
                )
            }
        }
        http.requestCache { it.disable() }
        http.formLogin { it.disable() }
        http.httpBasic { it.disable() }
        http.logout {
            it.logoutUrl("/api/v1/auth/logout").logoutSuccessHandler { _, response, _ ->
                response.status = 204
            }
        }
        http.addFilterAt(authentication, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
