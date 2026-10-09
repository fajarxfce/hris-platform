package dev.fajar.hris.sync.data.datasources

import dev.fajar.hris.sync.data.crypto.*
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class JceSyncCursorDataSource(private val keyring: SyncCursorKeyring) : SyncCursorDataSource {
    private val random = SecureRandom()

    override fun encrypt(bytes: ByteArray): String {
        require(bytes.size in 1..1024)
        val key = keyring.keys[keyring.activeId] ?: throw SyncCursorKeyUnavailableException()
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD("hris.mobile-sync.1.${keyring.activeId}".toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(bytes)
        return "1.${keyring.activeId}." +
            Base64.getUrlEncoder().withoutPadding().encodeToString(nonce + encrypted)
    }

    override fun decrypt(token: String): ByteArray {
        if (token.length !in 1..2048) throw InvalidSyncCursorException()
        val parts = token.split('.', limit = 4)
        if (parts.size != 3 || parts[0] != "1" || !parts[1].matches(Regex("[A-Za-z0-9_-]{1,32}")))
            throw InvalidSyncCursorException()
        if (keyring.keys.isEmpty()) throw SyncCursorKeyUnavailableException()
        val key = keyring.keys[parts[1]] ?: throw SyncCursorKeyRetiredException()
        val bytes = Base64.getUrlDecoder().decode(parts[2])
        if (bytes.size !in 29..1052) throw InvalidSyncCursorException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12))
        cipher.updateAAD("hris.mobile-sync.1.${parts[1]}".toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }
}
