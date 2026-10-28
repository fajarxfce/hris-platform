package dev.fajar.hris.identity.data.firebase.datasources

import dev.fajar.hris.identity.data.firebase.dto.FirebaseTokenData
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** JCA verifies RS256; the token cannot choose a key URL, algorithm or trust store. */
class JcaFirebaseTokenDataSource : FirebaseTokenDataSource {
    private val json =
        JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

    override fun keyId(idToken: String): String {
        val parts = parts(idToken)
        val header = json.readTree(decode(parts[0], 1024))
        require(header.isObject && header.properties().all { it.key in setOf("alg", "kid", "typ") })
        require(text(header, "alg") == "RS256")
        require(header.get("typ") == null || text(header, "typ") == "JWT")
        return text(header, "kid").also { require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
    }

    override fun verify(idToken: String, key: RSAPublicKey): FirebaseTokenData {
        keyId(idToken)
        require(key.modulus.bitLength() in 2048..4096)
        val parts = parts(idToken)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(key)
        signature.update("${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.US_ASCII))
        require(signature.verify(decode(parts[2], 1024)))
        val payload = json.readTree(decode(parts[1], 6144))
        require(payload.isObject)
        val firebase = requireNotNull(payload.get("firebase"))
        require(firebase.isObject)
        return FirebaseTokenData(
            text(payload, "iss"),
            text(payload, "sub"),
            text(payload, "aud"),
            epoch(payload, "iat"),
            epoch(payload, "exp"),
            epoch(payload, "auth_time"),
            text(firebase, "sign_in_provider"),
            if (firebase.get("tenant") == null) null else text(firebase, "tenant"),
        )
    }

    private fun parts(token: String): List<String> {
        require(token.length in 32..8192)
        return token.split('.').also { pieces ->
            require(
                pieces.size == 3 &&
                    pieces.all {
                        it.isNotEmpty() &&
                            it.all { c ->
                                c in 'a'..'z' ||
                                    c in 'A'..'Z' ||
                                    c in '0'..'9' ||
                                    c == '-' ||
                                    c == '_'
                            }
                    }
            )
        }
    }

    private fun decode(value: String, maximum: Int): ByteArray =
        Base64.getUrlDecoder().decode(value).also {
            require(
                it.size <= maximum &&
                    Base64.getUrlEncoder().withoutPadding().encodeToString(it) == value
            )
        }

    private fun text(node: JsonNode, name: String): String {
        val value = requireNotNull(node.get(name))
        require(value.isString)
        return value.asString()
    }

    private fun epoch(node: JsonNode, name: String): Long {
        val value = requireNotNull(node.get(name))
        require(value.isIntegralNumber && value.canConvertToLong())
        return value.asLong().also { require(it in 1..253_402_300_799L) }
    }
}
