package dev.fajar.hris.collaboration.delivery.di

import dev.fajar.hris.collaboration.delivery.matrix.MatrixPushCallbackErrorFilter
import dev.fajar.hris.collaboration.delivery.matrix.MatrixPushCallbackExecutor
import dev.fajar.hris.collaboration.delivery.matrix.writeMatrixGatewayDenied
import dev.fajar.hris.collaboration.domain.usecases.HandleCollaborationPush
import java.time.Clock
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.NullSecurityContextRepository
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class CollaborationWebConfiguration {
    @Bean
    fun collaborationPushCallbackErrors(settings: MatrixPushCallbackSettings) =
        org.springframework.boot.web.servlet
            .FilterRegistrationBean(MatrixPushCallbackErrorFilter(settings))
            .apply {
                setDispatcherTypes(jakarta.servlet.DispatcherType.ERROR)
                order = org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 30
            }

    @Bean
    fun collaborationPushCallbackSettings(environment: Environment) =
        matrixPushCallbackSettings(environment)

    @Bean(destroyMethod = "close")
    fun collaborationPushCallbackExecutor(handler: HandleCollaborationPush, clock: Clock) =
        MatrixPushCallbackExecutor(handler::accept, clock)

    /** Validate the actual socket read bound; do not change unrelated upload behavior. */
    @Bean
    fun collaborationPushCallbackSocketBound(
        settings: MatrixPushCallbackSettings
    ): WebServerFactoryCustomizer<TomcatServletWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            if (settings.enabled)
                factory.addConnectorCustomizers({ connector ->
                    val protocol =
                        connector.protocolHandler
                            as? org.apache.coyote.http11.AbstractHttp11Protocol<*>
                            ?: error("Matrix callback requires a bounded HTTP socket")
                    val timeout =
                        if (protocol.disableUploadTimeout) protocol.connectionTimeout
                        else protocol.connectionUploadTimeout
                    require(timeout in 1..5000) {
                        "Matrix callback socket body timeout must be at most five seconds"
                    }
                })
        }

    /** Matrix credentials must never be interpreted by the native bearer or cookie filter. */
    @Bean
    @Order(0)
    fun collaborationMatrixSecurity(http: HttpSecurity, json: ObjectMapper): SecurityFilterChain {
        http.securityMatcher("/_matrix/**", "/_synapse/**")
        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        http.securityContext { it.securityContextRepository(NullSecurityContextRepository()) }
        http.csrf { it.disable() }
        http.requestCache { it.disable() }
        http.logout { it.disable() }
        http.formLogin { it.disable() }
        http.httpBasic { it.disable() }
        http.authorizeHttpRequests {
            it.requestMatchers(HttpMethod.POST, "/_matrix/push/v1/notify").permitAll()
            it.requestMatchers(HttpMethod.POST, "/_matrix/client/v3/refresh").permitAll()
            it.requestMatchers(HttpMethod.POST, "/_matrix/client/unstable/dev.fajar.hris/pusher")
                .permitAll()
            it.requestMatchers(HttpMethod.POST, "/_matrix/client/v3/keys/upload").permitAll()
            it.requestMatchers(HttpMethod.POST, "/_matrix/client/v3/keys/query").permitAll()
            it.requestMatchers(HttpMethod.POST, "/_matrix/client/v3/keys/claim").permitAll()
            it.requestMatchers(HttpMethod.PUT, "/_matrix/client/v3/sendToDevice/m.room.encrypted/*")
                .permitAll()
            it.requestMatchers(HttpMethod.PUT, "/_matrix/client/v3/rooms/*/send/m.room.encrypted/*")
                .permitAll()
            it.requestMatchers(HttpMethod.GET, "/_matrix/client/v3/user/*/account_data/m.direct")
                .permitAll()
            it.requestMatchers(HttpMethod.PUT, "/_matrix/client/v3/user/*/account_data/m.direct")
                .permitAll()
            it.requestMatchers(HttpMethod.GET, "/_matrix/client/v3/account/whoami").permitAll()
            it.requestMatchers(HttpMethod.GET, "/_matrix/client/v3/sync").permitAll()
            it.requestMatchers(
                    HttpMethod.GET,
                    "/_matrix/client/versions",
                    "/_matrix/client/v3/capabilities",
                    "/_matrix/client/v3/rooms/*/state",
                    "/_matrix/client/v3/rooms/*/state/*",
                    "/_matrix/client/v3/rooms/*/state/*/",
                    "/_matrix/client/v3/rooms/*/members",
                    "/_matrix/client/v3/rooms/*/messages",
                    "/_matrix/client/v3/rooms/*/context/*",
                    "/_matrix/client/v3/rooms/*/event/*",
                )
                .permitAll()
            it.requestMatchers(
                    HttpMethod.POST,
                    "/_matrix/client/v3/rooms/*/receipt/m.read/*",
                    "/_matrix/client/v3/rooms/*/receipt/m.read.private/*",
                    "/_matrix/client/v3/rooms/*/join",
                    "/_matrix/client/v3/join/*",
                )
                .permitAll()
            it.anyRequest().denyAll()
        }
        http.exceptionHandling {
            it.authenticationEntryPoint { _, response, _ ->
                writeMatrixGatewayDenied(response, json)
            }
            it.accessDeniedHandler { _, response, _ -> writeMatrixGatewayDenied(response, json) }
        }
        return http.build()
    }
}
