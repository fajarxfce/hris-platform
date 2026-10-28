package dev.fajar.hris

import dev.fajar.hris.identity.data.firebase.datasources.FirebaseKeyDataSource
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import tools.jackson.databind.json.JsonMapper

@TestConfiguration(proxyBeanMethods = false)
class NativeFirebaseTestConfiguration {
    @Bean @Primary fun firebaseTestKeys() = FirebaseTestKeys()
}

/** Ephemeral signing material belongs only to the test context. */
class FirebaseTestKeys : FirebaseKeyDataSource {
    private val pair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val json = JsonMapper.builder().build()
    @Volatile var unavailable = false

    override fun keys(): Map<String, RSAPublicKey> {
        if (unavailable) throw IOException("Fixture unavailable")
        return mapOf("fixture" to pair.public as RSAPublicKey)
    }

    fun token(subject: String, now: Instant, overrides: Map<String, Any> = emptyMap()): String {
        val claims =
            mapOf(
                "iss" to "https://securetoken.google.com/cmp-fajar",
                "aud" to "cmp-fajar",
                "sub" to subject,
                "iat" to now.epochSecond,
                "exp" to now.plusSeconds(3600).epochSecond,
                "auth_time" to now.epochSecond,
                "firebase" to mapOf("sign_in_provider" to "password"),
            ) + overrides
        val header =
            encoded(
                json.writeValueAsBytes(mapOf("alg" to "RS256", "kid" to "fixture", "typ" to "JWT"))
            )
        val payload = encoded(json.writeValueAsBytes(claims))
        val input = "$header.$payload"
        val signature =
            Signature.getInstance("SHA256withRSA").run {
                initSign(pair.private)
                update(input.toByteArray(Charsets.US_ASCII))
                sign()
            }
        return "$input.${encoded(signature)}"
    }

    private fun encoded(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
