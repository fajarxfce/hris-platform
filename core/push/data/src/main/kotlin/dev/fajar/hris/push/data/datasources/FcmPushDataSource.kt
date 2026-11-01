package dev.fajar.hris.push.data.datasources

import dev.fajar.hris.push.data.transport.remainingPushDispatch
import dev.fajar.hris.push.data.transport.sendPushHttpRequest
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Clock
import java.time.Duration
import java.time.Instant

class FcmPushDataSource(
    private val client: HttpClient,
    private val endpoint: URI,
    private val timeout: Duration = Duration.ofSeconds(10),
    private val clock: Clock = Clock.systemUTC(),
) : PushDataSource {
    init {
        require(!timeout.isNegative && !timeout.isZero && timeout <= Duration.ofSeconds(10))
    }

    override fun send(accessToken: String, body: ByteArray, deadline: Instant): PushHttpResponse {
        require(body.size <= 8192)
        val budget = minOf(timeout, remainingPushDispatch(deadline, clock.instant()))
        val request =
            HttpRequest.newBuilder(endpoint)
                .timeout(budget)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
        val response =
            sendPushHttpRequest(
                client,
                request,
                minOf(budget, remainingPushDispatch(deadline, clock.instant())),
            )
        if (
            !response
                .headers()
                .firstValue("Content-Encoding")
                .orElse("identity")
                .equals("identity", true)
        )
            throw IOException("Encoded push response is unsupported")
        return PushHttpResponse(
            response.statusCode(),
            response.body(),
            response.headers().firstValue("Retry-After").orElse(null),
        )
    }
}
