package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.identity.data.crypto.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.util.encoders.Base32

class JceMfaCryptoDataSource(
    private val keyring: IdentityKeyring,
    private val random: SecureRandom = SecureRandom(),
) : MfaCryptoDataSource {
    override fun available(): Boolean = keyring.activeId in keyring.keys

    override fun newSecret(): String = Base32.toBase32String(ByteArray(20).also(random::nextBytes))

    override fun newRecoveryCode(): String =
        Base32.toBase32String(ByteArray(20).also(random::nextBytes))

    override fun encrypt(accountId: UUID, secret: String): String {
        val key = keyring.keys[keyring.activeId] ?: throw IdentityKeyUnavailableException()
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD("hris:totp:$accountId".toByteArray(Charsets.UTF_8))
        val bytes = secret.toByteArray(Charsets.UTF_8)
        val encrypted =
            try {
                cipher.doFinal(bytes)
            } finally {
                bytes.fill(0)
            }
        return listOf(
                keyring.activeId,
                Base64.getEncoder().encodeToString(nonce),
                Base64.getEncoder().encodeToString(encrypted),
            )
            .joinToString(".")
    }

    override fun decrypt(accountId: UUID, encrypted: String): String {
        require(encrypted.length <= 1024)
        val parts = encrypted.split('.')
        require(parts.size == 3)
        val key = keyring.keys[parts[0]] ?: throw IdentityKeyUnavailableException()
        val nonce = Base64.getDecoder().decode(parts[1])
        require(nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD("hris:totp:$accountId".toByteArray(Charsets.UTF_8))
        val plain = cipher.doFinal(Base64.getDecoder().decode(parts[2]))
        return try {
            String(plain, Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    }

    override fun matchCounter(secret: String, code: String, at: Instant): Long? {
        if (!code.matches(Regex("[0-9]{6}"))) return null
        require(secret.matches(Regex("[A-Z2-7]{32}")))
        val key = Base32.decode(secret)
        try {
            val counter = Math.floorDiv(at.epochSecond, 30L)
            for (candidate in (counter - 1)..(counter + 1)) {
                if (candidate < 0) continue
                val mac = Mac.getInstance("HmacSHA1")
                mac.init(SecretKeySpec(key, "HmacSHA1"))
                val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(candidate).array())
                val offset = hash.last().toInt() and 15
                val value = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
                val expected = (value % 1_000_000).toString().padStart(6, '0')
                if (
                    MessageDigest.isEqual(
                        expected.toByteArray(Charsets.US_ASCII),
                        code.toByteArray(Charsets.US_ASCII),
                    )
                )
                    return candidate
            }
            return null
        } finally {
            key.fill(0)
        }
    }

    override fun recoveryHash(code: String): String =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8))
            )
}
