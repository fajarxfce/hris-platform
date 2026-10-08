package dev.fajar.hris.core.http
/** An inclusive HTTP byte range. Parsing bounds both arithmetic and header complexity. */
data class ByteRange(val start: Long, val end: Long) {
    val length: Long
        get() = end - start + 1
}

fun parseByteRange(header: String, size: Long): ByteRange? {
    if (size <= 0 || header.length > 80 || !header.startsWith("bytes=") || ',' in header)
        return null
    val parts = header.removePrefix("bytes=").split('-', limit = 3)
    if (
        parts.size != 2 || parts.any { value -> value.length > 19 || value.any { it !in '0'..'9' } }
    )
        return null
    if (parts[0].isEmpty()) {
        val suffix = parts[1].toLongOrNull() ?: return null
        return if (suffix > 0) ByteRange(maxOf(0, size - suffix), size - 1) else null
    }
    val start = parts[0].toLongOrNull() ?: return null
    val end = if (parts[1].isEmpty()) size - 1 else parts[1].toLongOrNull() ?: return null
    return if (start < size && end >= start) ByteRange(start, minOf(end, size - 1)) else null
}

fun matchesDownloadEtag(header: String, etag: String): Boolean {
    if (header.length > 2048) return false
    val tokens = header.split(',', limit = 17)
    if (tokens.size > 16) return false
    return tokens.any { it.trim() == "*" || it.trim().removePrefix("W/") == etag }
}
