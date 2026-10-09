package dev.fajar.hris.sync.data.crypto

import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class SyncCursorKeyring(val activeId: String, keys: Map<String, SecretKey>) {
    val keys = keys.toMap()

    override fun toString() = "SyncCursorKeyring(<redacted>)"
}

fun parseSyncCursorKeyring(activeId: String, encoded: String): SyncCursorKeyring {
    require(activeId.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { "Invalid sync key identifier" }
    require(encoded.length <= 4096) { "Invalid sync key configuration" }
    if (encoded.isBlank()) return SyncCursorKeyring(activeId, emptyMap())
    val entries = encoded.split(',')
    require(entries.size in 1..8) { "Invalid sync key configuration" }
    val keys = linkedMapOf<String, SecretKey>()
    for (entry in entries) {
        val fields = entry.trim().split(':', limit = 2)
        require(
            fields.size == 2 &&
                fields[0].matches(Regex("[A-Za-z0-9_-]{1,32}")) &&
                fields[0] !in keys
        ) {
            "Invalid sync key configuration"
        }
        val bytes =
            try {
                Base64.getDecoder().decode(fields[1])
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid sync key configuration")
            }
        try {
            require(bytes.size == 32) { "Sync cursors require a 256-bit key" }
            keys[fields[0]] = SecretKeySpec(bytes, "AES")
        } finally {
            bytes.fill(0)
        }
    }
    require(activeId in keys) { "Active sync key is missing" }
    return SyncCursorKeyring(activeId, keys)
}
