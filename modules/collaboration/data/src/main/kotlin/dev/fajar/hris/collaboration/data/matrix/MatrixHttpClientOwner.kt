package dev.fajar.hris.collaboration.data.matrix

import java.net.http.HttpClient
import java.time.Duration

/** One client per application context; individual exchanges remain owned by their datasource. */
class MatrixHttpClientOwner : AutoCloseable {
    val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    override fun close() {
        client.shutdownNow()
        try {
            check(client.awaitTermination(Duration.ofSeconds(5))) {
                "Matrix HTTP client did not terminate"
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
