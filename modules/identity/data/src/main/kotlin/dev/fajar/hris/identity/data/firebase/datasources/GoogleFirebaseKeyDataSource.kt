package dev.fajar.hris.identity.data.firebase.datasources

import dev.fajar.hris.identity.data.firebase.errors.FirebaseKeysUnavailableException
import dev.fajar.hris.identity.data.firebase.transport.BoundedFirebaseBodySubscriber
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

/** One application-owned key cache; unknown token key IDs never choose or refresh a URL. */
class GoogleFirebaseKeyDataSource(
    private val client: HttpClient,
    private val clock: Clock,
    private val endpoint: URI =
        URI(
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
        ),
) : FirebaseKeyDataSource {
    private val json =
        JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()
    private val access = ReentrantLock()
    private var cached: Map<String, RSAPublicKey> = emptyMap()
    private var validUntil = Instant.MIN
    private var retryAfter = Instant.MIN

    override fun keys(): Map<String, RSAPublicKey> {
        if (!access.tryLock(5, TimeUnit.SECONDS)) throw FirebaseKeysUnavailableException()
        try {
            val now = clock.instant()
            if (now < validUntil) return cached
            if (now < retryAfter) throw FirebaseKeysUnavailableException()
            retryAfter = now.plusSeconds(30)
            val request =
                HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "identity")
                    .GET()
                    .build()
            val pending =
                client.sendAsync(
                    request,
                    HttpResponse.BodyHandler { BoundedFirebaseBodySubscriber(65536) },
                )
            val response =
                try {
                    pending.get(5, TimeUnit.SECONDS)
                } finally {
                    if (!pending.isDone) pending.cancel(true)
                }
            require(response.statusCode() == 200)
            require(
                response
                    .headers()
                    .firstValue("Content-Encoding")
                    .orElse("identity")
                    .equals("identity", true)
            )
            require(
                response
                    .headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .substringBefore(';')
                    .trim()
                    .equals("application/json", true)
            )
            val control = response.headers().firstValue("Cache-Control").orElse("")
            val maxAge =
                Regex("(?:^|,)\\s*max-age=(\\d+)\\s*(?=,|$)", RegexOption.IGNORE_CASE)
                    .find(control)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
            require(maxAge != null && maxAge in 0..86400)
            val age = response.headers().firstValue("Age").orElse("0").toLong()
            require(age in 0..86400)
            val value = json.readTree(response.body())
            require(value.isObject && value.size() in 1..10)
            var expiry = clock.instant().plusSeconds((maxAge - age).coerceAtLeast(0))
            val keys =
                value.properties().associate { entry ->
                    require(
                        entry.key.matches(Regex("[A-Za-z0-9_-]{1,128}")) && entry.value.isString
                    )
                    val pem = entry.value.asString()
                    require(pem.length in 100..8192)
                    val cert =
                        ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII)).use {
                            CertificateFactory.getInstance("X.509").generateCertificate(it)
                                as X509Certificate
                        }
                    cert.checkValidity(Date.from(clock.instant()))
                    expiry = minOf(expiry, cert.notAfter.toInstant())
                    val key = cert.publicKey as RSAPublicKey
                    require(key.modulus.bitLength() in 2048..4096)
                    entry.key to key
                }
            cached = keys.toMap()
            validUntil = expiry
            return cached
        } catch (error: Exception) {
            if (error is InterruptedException || error is CancellationException) throw error
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            throw FirebaseKeysUnavailableException()
        } finally {
            access.unlock()
        }
    }
}
