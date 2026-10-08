package dev.fajar.hris.identity.data.crypto

import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class IdentityKeyring(val activeId: String, keys: Map<String, SecretKey>) {
    val keys: Map<String, SecretKey> = keys.toMap()

    override fun toString(): String = "IdentityKeyring(<redacted>)"
}

fun parseIdentityKeyring(activeId: String, encoded: String): IdentityKeyring {
    require(activeId.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { "Invalid identity key identifier" }
    require(encoded.length <= 4096) { "Invalid identity key configuration" }
    if (encoded.isBlank()) return IdentityKeyring(activeId, emptyMap())
    val entries = encoded.split(',')
    require(entries.size in 1..8) { "Invalid identity key configuration" }
    val keys = linkedMapOf<String, SecretKey>()
    for (entry in entries) {
        val fields = entry.trim().split(':', limit = 2)
        require(
            fields.size == 2 &&
                fields[0].matches(Regex("[A-Za-z0-9_-]{1,32}")) &&
                fields[0] !in keys
        ) {
            "Invalid identity key configuration"
        }
        val bytes =
            try {
                Base64.getDecoder().decode(fields[1])
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid identity key configuration")
            }
        try {
            require(bytes.size == 32) { "Identity encryption requires a 256-bit key" }
            keys[fields[0]] = SecretKeySpec(bytes, "AES")
        } finally {
            bytes.fill(0)
        }
    }
    require(activeId in keys) { "Active identity key is missing" }
    return IdentityKeyring(activeId, keys.toMap())
}
