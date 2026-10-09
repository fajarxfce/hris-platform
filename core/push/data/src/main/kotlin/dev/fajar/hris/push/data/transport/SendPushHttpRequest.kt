package dev.fajar.hris.push.data.transport

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * JDK request timeout alone stops at headers. This owner also bounds and cancels body reception.
 */
fun sendPushHttpRequest(
    client: HttpClient,
    request: HttpRequest,
    timeout: Duration,
): HttpResponse<ByteArray> {
    val response =
        client.sendAsync(request, HttpResponse.BodyHandler { BoundedPushBodySubscriber(65536) })
    return try {
        response.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        throw HttpTimeoutException("Push HTTP exchange exceeded its deadline")
    } finally {
        if (!response.isDone) response.cancel(true)
    }
}
