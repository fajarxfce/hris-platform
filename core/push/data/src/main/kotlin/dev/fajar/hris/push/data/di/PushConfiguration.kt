package dev.fajar.hris.push.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.data.datasources.*
import dev.fajar.hris.push.data.repositories.FcmPushRepository
import dev.fajar.hris.push.data.transport.GooglePushAuthTransport
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class PushConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    fun pushHttpClient(): HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    @Bean
    fun pushRepository(
        environment: Environment,
        pushHttpClient: HttpClient,
        json: ObjectMapper,
        clock: Clock,
    ): PushRepository {
        if (!environment.getProperty("HRIS_FCM_ENABLED", Boolean::class.java, false))
            return PushRepository {
                Result.Failed(Failure(FailureKind.UNAVAILABLE, "push_not_configured"))
            }
        val project = environment.getRequiredProperty("HRIS_FCM_PROJECT_ID")
        val credentials =
            readGooglePushCredentials(
                Path.of(environment.getRequiredProperty("HRIS_FCM_CREDENTIALS_FILE")),
                project,
                GooglePushAuthTransport(pushHttpClient),
                json,
            )
        return FcmPushRepository(
            GooglePushCredentialDataSource(credentials),
            FcmPushDataSource(
                pushHttpClient,
                URI("https://fcm.googleapis.com/v1/projects/$project/messages:send"),
            ),
            json,
            clock,
        )
    }
}
