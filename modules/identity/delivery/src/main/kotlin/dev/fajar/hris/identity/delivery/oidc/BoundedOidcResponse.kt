package dev.fajar.hris.identity.delivery.oidc

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import org.springframework.http.client.ClientHttpResponse

/** A transport limit for token/JWK documents, including chunked responses. */
class BoundedOidcResponse(private val delegate: ClientHttpResponse) :
    ClientHttpResponse by delegate {
    private val bounded =
        object : FilterInputStream(delegate.body) {
            private var count = 0L

            override fun read(): Int {
                val value = super.read()
                if (value >= 0) account(1)
                return value
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                val size = super.read(bytes, offset, length.coerceAtMost(1048577 - count.toInt()))
                if (size > 0) account(size)
                return size
            }

            private fun account(size: Int) {
                count += size
                if (count > 1048576) throw IOException("OIDC response limit exceeded")
            }
        }

    override fun getBody(): InputStream = bounded
}
