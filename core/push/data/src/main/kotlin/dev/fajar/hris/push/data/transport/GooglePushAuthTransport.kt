package dev.fajar.hris.push.data.transport

import com.google.api.client.http.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * SDK adapter only. The application owns its client; synchronous OAuth refresh uses no executor.
 */
class GooglePushAuthTransport(
    private val client: HttpClient,
    private val tokenEndpoint: URI = URI("https://oauth2.googleapis.com/token"),
    private val timeout: Duration = Duration.ofSeconds(10),
) : HttpTransport() {
    init {
        require(!timeout.isNegative && !timeout.isZero && timeout <= Duration.ofSeconds(10))
    }

    override fun buildRequest(method: String, url: String): LowLevelHttpRequest {
        if (method != "POST" || URI(url) != tokenEndpoint)
            throw IOException("Unsupported OAuth endpoint")
        return object : LowLevelHttpRequest() {
            private val headers = linkedMapOf<String, String>()

            override fun addHeader(name: String, value: String) {
                if (!name.equals("Accept-Encoding", true)) headers[name] = value
            }

            override fun execute(): LowLevelHttpResponse {
                val output =
                    object : ByteArrayOutputStream() {
                        override fun write(value: ByteArray, offset: Int, length: Int) {
                            if (length > 65536 - size())
                                throw IOException("OAuth request exceeds its byte limit")
                            super.write(value, offset, length)
                        }

                        override fun write(value: Int) {
                            if (size() >= 65536)
                                throw IOException("OAuth request exceeds its byte limit")
                            super.write(value)
                        }
                    }
                streamingContent?.writeTo(output)
                val request =
                    HttpRequest.newBuilder(tokenEndpoint)
                        .timeout(timeout)
                        .header("Accept-Encoding", "identity")
                headers.forEach { (name, value) -> request.header(name, value) }
                contentType?.let { request.setHeader("Content-Type", it) }
                if (contentEncoding != null)
                    throw IOException("Encoded OAuth requests are unsupported")
                val response =
                    sendPushHttpRequest(
                        client,
                        request
                            .POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()))
                            .build(),
                        timeout,
                    )
                val encoding = response.headers().firstValue("Content-Encoding").orElse("identity")
                if (!encoding.equals("identity", true))
                    throw IOException("Encoded OAuth response is unsupported")
                return GooglePushAuthResponse(response)
            }
        }
    }
}

private class GooglePushAuthResponse(private val response: HttpResponse<ByteArray>) :
    LowLevelHttpResponse() {
    private val stream = ByteArrayInputStream(response.body())
    private val headers =
        response.headers().map().flatMap { (name, values) -> values.map { name to it } }

    override fun getContent() = stream

    override fun getContentEncoding(): String? = null

    override fun getContentLength() = response.body().size.toLong()

    override fun getContentType(): String? =
        response.headers().firstValue("Content-Type").orElse(null)

    override fun getStatusLine() = "HTTP ${response.statusCode()}"

    override fun getStatusCode() = response.statusCode()

    override fun getReasonPhrase() = ""

    override fun getHeaderCount() = headers.size

    override fun getHeaderName(index: Int) = headers[index].first

    override fun getHeaderValue(index: Int) = headers[index].second

    override fun disconnect() {
        stream.close()
    }
}
