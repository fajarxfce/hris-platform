package dev.fajar.hris.documents.data

import dev.fajar.hris.documents.domain.entities.DocumentContentPart
import dev.fajar.hris.storage.data.datasources.ObjectStorageDataSource
import dev.fajar.hris.storage.data.models.ObjectMetadataData
import java.security.MessageDigest
import java.util.HexFormat

class InspectionStorageFixture(val parts: List<ByteArray>) : ObjectStorageDataSource {
    var error: Exception? = null
    val manifest =
        parts.mapIndexed { index, bytes ->
            DocumentContentPart(
                "fixture/$index",
                parts.take(index).sumOf { it.size.toLong() },
                bytes.size,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                "fixture",
            )
        }

    override fun read(key: String, offset: Long, length: Int, etag: String): ByteArray {
        error?.let { throw it }
        val bytes = parts[key.substringAfter('/').toInt()]
        check(offset == 0L && length == bytes.size)
        return bytes
    }

    override fun list(
        prefix: String,
        afterKey: String?,
        limit: Int,
    ): dev.fajar.hris.storage.data.models.ObjectInventoryPageData =
        throw UnsupportedOperationException("Listing is not part of this fixture")

    override fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData =
        throw UnsupportedOperationException()

    override fun metadata(key: String): ObjectMetadataData = throw UnsupportedOperationException()

    override fun delete(key: String) {
        throw UnsupportedOperationException()
    }

    override fun close() {}
}
