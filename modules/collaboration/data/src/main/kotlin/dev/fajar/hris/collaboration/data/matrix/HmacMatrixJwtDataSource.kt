package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import tools.jackson.databind.json.JsonMapper

class HmacMatrixJwtDataSource(private val configuration: MatrixProviderConfiguration) :
    MatrixJwtDataSource {
    private val json = JsonMapper.builder().build()
    private val encoding = Base64.getUrlEncoder().withoutPadding()

    override fun sign(subject: String, issuedAt: Instant, expiresAt: Instant): String {
        require(subject.matches(Regex("hris_[a-f0-9]{32}_[a-f0-9]{32}")))
        require(expiresAt > issuedAt && expiresAt <= issuedAt.plusSeconds(60))
        require(expiresAt.epochSecond > issuedAt.epochSecond)
        val header =
            encoding.encodeToString(json.writeValueAsBytes(mapOf("alg" to "HS256", "typ" to "JWT")))
        val claims =
            encoding.encodeToString(
                json.writeValueAsBytes(
                    mapOf(
                        "iss" to configuration.issuer,
                        "aud" to configuration.audience,
                        "sub" to subject,
                        "iat" to issuedAt.epochSecond,
                        "nbf" to issuedAt.epochSecond,
                        "exp" to expiresAt.epochSecond,
                    )
                )
            )
        val payload = "$header.$claims"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(
            SecretKeySpec(configuration.jwtSecret.toByteArray(Charsets.US_ASCII), "HmacSHA256")
        )
        return "$payload.${encoding.encodeToString(mac.doFinal(payload.toByteArray(Charsets.US_ASCII)))}"
    }
}
