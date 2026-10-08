package dev.fajar.hris

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import tools.jackson.module.kotlin.jacksonObjectMapper

class OidcTestProvider : AutoCloseable {
    private val json = jacksonObjectMapper()
    private val key = RSAKeyGenerator(2048).keyID("fixture-key").generate()
    private val wrongKey = RSAKeyGenerator(2048).keyID("fixture-key").generate()
    private val executor =
        ThreadPoolExecutor(
            2,
            2,
            0,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(32),
            ThreadPoolExecutor.AbortPolicy(),
        )
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8).apply {
            executor = this@OidcTestProvider.executor
        }
    val issuer: String = "http://127.0.0.1:${server.address.port}"
    val exchanges = AtomicInteger()

    private data class Grant(
        val nonce: String,
        val challenge: String,
        val subject: String,
        val invalid: String?,
    )

    private val grants = ConcurrentHashMap<String, Grant>()

    init {
        server.createContext("/jwks") { exchange ->
            val body = json.writeValueAsBytes(JWKSet(key.toPublicJWK()).toJSONObject())
            try {
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.write(body)
            } finally {
                exchange.close()
            }
        }
        server.createContext("/token") { exchange ->
            try {
                exchanges.incrementAndGet()
                val raw = exchange.requestBody.readNBytes(16385)
                check(raw.size <= 16384)
                val form = parameters(String(raw, UTF_8))
                val grant = grants.remove(form["code"])
                val basic =
                    "Basic " +
                        Base64.getEncoder()
                            .encodeToString("fixture-client:fixture-secret".toByteArray(UTF_8))
                val challenge =
                    form["code_verifier"]?.let {
                        Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                MessageDigest.getInstance("SHA-256").digest(it.toByteArray(UTF_8))
                            )
                    }
                val valid =
                    grant != null &&
                        challenge == grant.challenge &&
                        exchange.requestHeaders.getFirst("Authorization") == basic &&
                        form["grant_type"] == "authorization_code"
                val body =
                    if (!valid) json.writeValueAsBytes(mapOf("error" to "invalid_grant"))
                    else {
                        val now = Instant.now()
                        val claims =
                            JWTClaimsSet.Builder()
                                .issuer(
                                    if (grant.invalid == "issuer") "https://untrusted.example.test"
                                    else issuer
                                )
                                .subject(grant.subject)
                                .audience(
                                    if (grant.invalid == "audience") "another-client"
                                    else "fixture-client"
                                )
                                .issueTime(Date.from(now.minusSeconds(10)))
                                .expirationTime(
                                    Date.from(
                                        now.plusSeconds(
                                            if (grant.invalid == "expiry") -600 else 300
                                        )
                                    )
                                )
                                .claim(
                                    "nonce",
                                    if (grant.invalid == "nonce") "different-nonce" else grant.nonce,
                                )
                                .claim("email", "admin@example.test")
                                .claim("email_verified", true)
                                .claim("amr", listOf("mfa"))
                                .claim("roles", listOf("SUPER_ADMIN"))
                                .build()
                        val jwt =
                            SignedJWT(
                                JWSHeader.Builder(JWSAlgorithm.RS256).keyID("fixture-key").build(),
                                claims,
                            )
                        jwt.sign(RSASSASigner(if (grant.invalid == "signature") wrongKey else key))
                        json.writeValueAsBytes(
                            mapOf(
                                "access_token" to "fictional-provider-access-token",
                                "refresh_token" to "fictional-provider-refresh-token",
                                "token_type" to "Bearer",
                                "expires_in" to 300,
                                "scope" to "openid",
                                "id_token" to jwt.serialize(),
                            )
                        )
                    }
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(if (valid) 200 else 400, body.size.toLong())
                exchange.responseBody.write(body)
            } catch (error: Exception) {
                exchange.sendResponseHeaders(500, -1)
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    fun issue(authorization: URI, subject: String, invalid: String? = null): String {
        check(grants.size < 64)
        val params = parameters(requireNotNull(authorization.rawQuery))
        check(params["code_challenge_method"] == "S256")
        val code = UUID.randomUUID().toString()
        grants[code] =
            Grant(
                requireNotNull(params["nonce"]),
                requireNotNull(params["code_challenge"]),
                subject,
                invalid,
            )
        return code
    }

    fun clear() {
        grants.clear()
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
        check(executor.awaitTermination(5, TimeUnit.SECONDS))
        grants.clear()
    }

    companion object {
        fun parameters(query: String): Map<String, String> =
            query.split('&').associate { field ->
                val pair = field.split('=', limit = 2)
                URLDecoder.decode(pair[0], UTF_8) to
                    URLDecoder.decode(pair.getOrElse(1) { "" }, UTF_8)
            }
    }
}
