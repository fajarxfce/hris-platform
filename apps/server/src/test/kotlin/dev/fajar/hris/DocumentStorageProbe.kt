package dev.fajar.hris

import dev.fajar.hris.storage.data.datasources.ObjectStorageDataSource
import dev.fajar.hris.storage.data.models.ObjectMetadataData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager

class DocumentStorageProbe : ObjectStorageDataSource {
    data class Value(
        val bytes: ByteArray,
        val sha256: String,
        val modifiedAt: java.time.Instant = java.time.Instant.now(),
    )

    val objects = ConcurrentHashMap<String, Value>()
    val calls = AtomicInteger()
    @Volatile var beforeWrite: ((String) -> Unit)? = null
    @Volatile var fail = false
    @Volatile var beforeRead: (() -> Unit)? = null
    val reads = AtomicInteger()
    val listings = AtomicInteger()
    val cursors = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile var beforeList: (() -> Unit)? = null
    @Volatile var failList = false

    override fun list(
        prefix: String,
        afterKey: String?,
        limit: Int,
    ): dev.fajar.hris.storage.data.models.ObjectInventoryPageData {
        check(!TransactionSynchronizationManager.isActualTransactionActive())
        listings.incrementAndGet()
        cursors += afterKey ?: ""
        val order =
            Comparator<String> { a, b ->
                java.util.Arrays.compareUnsigned(
                    a.toByteArray(Charsets.UTF_8),
                    b.toByteArray(Charsets.UTF_8),
                )
            }
        val rows =
            objects.entries
                .filter {
                    it.key.startsWith(prefix) &&
                        (afterKey == null || order.compare(it.key, afterKey) > 0)
                }
                .sortedWith { a, b -> order.compare(a.key, b.key) }
                .take(limit + 1)
        val page =
            dev.fajar.hris.storage.data.models.ObjectInventoryPageData(
                rows.take(limit).map {
                    dev.fajar.hris.storage.data.models.ObjectInventoryEntryData(
                        it.key,
                        it.value.bytes.size.toLong(),
                        it.value.sha256,
                        it.value.modifiedAt,
                    )
                },
                rows.size > limit,
            )
        beforeList?.invoke()
        if (failList) throw java.io.IOException("Inventory fixture unavailable")
        return page
    }

    override fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData {
        check(!TransactionSynchronizationManager.isActualTransactionActive())
        calls.incrementAndGet()
        beforeWrite?.invoke(key)
        if (fail) throw java.io.IOException("fixture-only-unavailable")
        objects[key] = Value(bytes.copyOf(), sha256)
        return ObjectMetadataData(bytes.size.toLong(), "\"$sha256\"", sha256)
    }

    override fun metadata(key: String): ObjectMetadataData {
        val value = objects.getValue(key)
        return ObjectMetadataData(value.bytes.size.toLong(), "\"${value.sha256}\"", value.sha256)
    }

    override fun read(key: String, offset: Long, length: Int, etag: String): ByteArray {
        check(!TransactionSynchronizationManager.isActualTransactionActive())
        beforeRead?.invoke()
        reads.incrementAndGet()
        return objects.getValue(key).bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
    }

    override fun delete(key: String) {
        check(!TransactionSynchronizationManager.isActualTransactionActive())
        objects.remove(key)
    }

    override fun close() {
        objects.clear()
        beforeWrite = null
        beforeRead = null
        beforeList = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class DocumentStorageProbeConfiguration {
    @Bean @Primary fun documentStorageProbe() = DocumentStorageProbe()
}
