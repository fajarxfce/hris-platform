package dev.fajar.hris

import dev.fajar.hris.documents.data.datasources.DocumentScanDataSource
import dev.fajar.hris.documents.data.datasources.DocumentScanSession
import dev.fajar.hris.documents.data.models.DocumentScanData
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager

class DocumentScanProbe : DocumentScanDataSource {
    @Volatile var beforeFinish: (() -> Unit)? = null
    @Volatile var clean = true
    val open = AtomicInteger()
    val closed = AtomicInteger()

    override fun open(): DocumentScanSession {
        check(!TransactionSynchronizationManager.isActualTransactionActive())
        open.incrementAndGet()
        return object : DocumentScanSession {
            override fun write(bytes: ByteArray) {
                check(!TransactionSynchronizationManager.isActualTransactionActive())
                check(bytes.isNotEmpty())
            }

            override fun finish(): DocumentScanData {
                check(!TransactionSynchronizationManager.isActualTransactionActive())
                beforeFinish?.invoke()
                return DocumentScanData(clean, "ClamAV fixture/1")
            }

            override fun close() {
                closed.incrementAndGet()
            }
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class DocumentScanProbeConfiguration {
    @Bean @Primary fun documentScanProbe() = DocumentScanProbe()
}
