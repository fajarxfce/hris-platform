package dev.fajar.hris.identity.data.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

fun encryptIdentityEnvelope(
    keyring: IdentityKeyring,
    purpose: String,
    value: String,
    random: SecureRandom,
): String {
    require(value.length <= 2048 && purpose.length <= 256)
    val key = keyring.keys[keyring.activeId] ?: throw IdentityKeyUnavailableException()
    val nonce = ByteArray(12).also(random::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
    cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
    val plain = value.toByteArray(Charsets.UTF_8)
    val encrypted =
        try {
            cipher.doFinal(plain)
        } finally {
            plain.fill(0)
        }
    return listOf(
            keyring.activeId,
            Base64.getEncoder().encodeToString(nonce),
            Base64.getEncoder().encodeToString(encrypted),
        )
        .joinToString(".")
}

fun decryptIdentityEnvelope(keyring: IdentityKeyring, purpose: String, encrypted: String): String {
    require(encrypted.length <= 4096 && purpose.length <= 256)
    val parts = encrypted.split('.')
    require(parts.size == 3)
    val key = keyring.keys[parts[0]] ?: throw IdentityKeyUnavailableException()
    val nonce = Base64.getDecoder().decode(parts[1])
    require(nonce.size == 12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
    cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
    val plain = cipher.doFinal(Base64.getDecoder().decode(parts[2]))
    return try {
        String(plain, Charsets.UTF_8)
    } finally {
        plain.fill(0)
    }
}
