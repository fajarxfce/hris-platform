package dev.fajar.hris.collaboration.delivery.matrix

import java.net.InetAddress
import org.springframework.security.web.util.matcher.IpAddressMatcher

/**
 * Socket peers only. Hostnames, forwarded headers, scoped addresses and DNS are never consulted.
 */
class MatrixPushCallbackSources(entries: List<String>) {
    private val networks: List<IpAddressMatcher>

    init {
        require(entries.size in 1..32) { "Configure one to 32 Matrix callback source networks" }
        networks =
            entries.map { entry ->
                require(entry.length in 1..64 && entry.trim() == entry) {
                    "Invalid Matrix callback source"
                }
                val parts = entry.split('/')
                require(parts.size in 1..2) { "Invalid Matrix callback source" }
                val address =
                    requireNotNull(literal(parts[0])) { "Matrix callback source must be numeric" }
                val bits = address.address.size * 8
                val prefix = if (parts.size == 2) parts[1].toIntOrNull() else bits
                require(prefix != null && prefix in 1..bits) {
                    "Invalid Matrix callback source prefix"
                }
                IpAddressMatcher(address.hostAddress + "/" + prefix)
            }
    }

    fun allows(remoteAddress: String): Boolean {
        val address = literal(remoteAddress) ?: return false
        return networks.any { it.matches(address.hostAddress) }
    }

    override fun toString() = "MatrixPushCallbackSources(<restricted>)"

    private fun literal(value: String): InetAddress? {
        if (value.length !in 2..45) return null
        if (':' in value) {
            if (value.count { it == ':' } < 2 || value.any { it !in "0123456789abcdefABCDEF:." })
                return null
            return try {
                InetAddress.getByName(value)
            } catch (_: java.net.UnknownHostException) {
                null
            }
        }
        val pieces = value.split('.')
        if (pieces.size != 4) return null
        val bytes = ByteArray(4)
        for ((index, piece) in pieces.withIndex()) {
            if (
                piece.length !in 1..3 ||
                    piece.any { it !in '0'..'9' } ||
                    (piece.length > 1 && piece[0] == '0')
            )
                return null
            val number = piece.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            bytes[index] = number.toByte()
        }
        return InetAddress.getByAddress(bytes)
    }
}
