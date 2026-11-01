package dev.fajar.hris.collaboration.delivery.di

import dev.fajar.hris.collaboration.delivery.matrix.writeMatrixGatewayDenied
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.NullSecurityContextRepository
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class CollaborationWebConfiguration {
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
