package dev.fajar.hris.identity.delivery.oidc

import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.*
import org.springframework.http.client.JdkClientHttpRequestFactory

/**
 * Spring owns this bean separately so failed protocol configuration still releases HTTP resources.
 */
class OidcHttpTransport : AutoCloseable {
    private val executor =
        ThreadPoolExecutor(
            4,
            4,
            0,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(64),
            Thread.ofPlatform().name("hris-oidc-http-", 0).factory(),
            ThreadPoolExecutor.AbortPolicy(),
        )
    private val client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .executor(executor)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    val requests =
        JdkClientHttpRequestFactory(client).apply { setReadTimeout(Duration.ofSeconds(8)) }

    override fun close() {
        client.shutdownNow()
        executor.shutdownNow()
        try {
            client.awaitTermination(Duration.ofSeconds(5))
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
