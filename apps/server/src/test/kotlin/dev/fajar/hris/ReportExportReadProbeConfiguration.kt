package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.domain.entities.*
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*
import org.springframework.transaction.support.TransactionSynchronizationManager

@TestConfiguration(proxyBeanMethods = false)
class ReportExportReadProbeConfiguration {
    @Bean @Primary fun reportExportReadProbe() = ReportExportReadProbe()
}

class ReportExportReadProbe : ObjectStorageRepository {
    val objects = ConcurrentHashMap<String, Pair<StoredObject, ByteArray>>()
    var afterRead: () -> Unit = {}
    var modify: (ByteArray) -> ByteArray = { it }
    var reads = 0

    override fun put(key: String, bytes: ByteArray): Result<StoredObject> {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
        val digest =
            java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
        val metadata = StoredObject(key, bytes.size.toLong(), UUID.randomUUID().toString(), digest)
        assertNull(objects.putIfAbsent(key, metadata to bytes.copyOf()))
        return Result.Success(metadata)
    }

    override fun read(key: String, offset: Long, length: Int, etag: String): Result<ByteArray> {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
        val (metadata, bytes) = objects.getValue(key)
        assertEquals(metadata.etag, etag)
        assertEquals(0, offset)
        assertEquals(bytes.size, length)
        reads++
        afterRead()
        return Result.Success(modify(bytes.copyOf()))
    }

    override fun metadata(key: String) = Result.Success(objects[key]?.first)

    override fun delete(key: String): Result<Unit> {
        objects.remove(key)
        return Result.Success(Unit)
    }

    override fun list(companyId: UUID, afterKey: String?, limit: Int): Result<ObjectInventoryPage> =
        error("Unexpected inventory")
}
