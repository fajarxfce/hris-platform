package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.identity.data.crypto.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

class JceIdentityTokenDataSource(
    private val keyring: IdentityKeyring,
    private val random: SecureRandom = SecureRandom(),
) : IdentityTokenDataSource {
    override fun generate(): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(32).also(random::nextBytes))

    override fun hash(token: String): String =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII))
            )

    override fun encrypt(purpose: String, value: String): String =
        encryptIdentityEnvelope(keyring, purpose, value, random)

    override fun decrypt(purpose: String, value: String): String =
        decryptIdentityEnvelope(keyring, purpose, value)
}
