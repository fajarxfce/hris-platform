package dev.fajar.hris.documents.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.*
import dev.fajar.hris.documents.data.errors.*
import dev.fajar.hris.documents.data.models.DocumentScanData
import dev.fajar.hris.documents.data.repositories.InspectedDocumentRepository
import dev.fajar.hris.storage.data.errors.StoredObjectVersionChanged
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentInspectionBoundaryTest {
    private class Scan : DocumentScanDataSource {
        var opened = 0
        var closed = 0
        var closingFailure = false
        var interruptAtOpen = false

        override fun open(): DocumentScanSession {
            opened++
            if (interruptAtOpen) Thread.currentThread().interrupt()
            return object : DocumentScanSession {
                override fun write(bytes: ByteArray) {}

                override fun finish() = DocumentScanData(true, "ClamAV fixture")

                override fun close() {
                    closed++
                    if (closingFailure) throw java.io.IOException("fixture close")
                }
            }
        }
    }

    @Test
    fun existingStorageFailureSurvivesScannerCleanupFailure() {
        val storage = InspectionStorageFixture(listOf(byteArrayOf(1)))
        storage.error = StoredObjectVersionChanged()
        val scan = Scan().apply { closingFailure = true }
        val result =
            InspectedDocumentRepository(storage, scan, TikaDocumentMediaTypeDataSource())
                .inspect(storage.manifest)
        assertEquals(FailureKind.CONFLICT, (result as Result.Failed).failure.kind)
        assertEquals("stored_object_changed", result.failure.code)
        assertEquals(1, scan.closed)
    }

    @Test
    fun cancellationAndLateInterruptionCloseAcquiredSessions() {
        for (late in listOf(false, true)) {
            val storage = InspectionStorageFixture(listOf(byteArrayOf(1)))
            val scan = Scan().apply { interruptAtOpen = late }
            if (!late) storage.error = CancellationException("fixture")
            try {
                assertThrows(
                    if (late) InterruptedException::class.java
                    else CancellationException::class.java
                ) {
                    InspectedDocumentRepository(storage, scan, TikaDocumentMediaTypeDataSource())
                        .inspect(storage.manifest)
                }
            } finally {
                Thread.interrupted()
            }
            assertEquals(1, scan.opened)
            assertEquals(1, scan.closed)
        }
    }

    @Test
    fun corruptPartsAndMissingScannerNeverProduceAValidInspection() {
        val storage = InspectionStorageFixture(listOf(byteArrayOf(1, 2, 3)))
        val scan = Scan()
        val repo = InspectedDocumentRepository(storage, scan, TikaDocumentMediaTypeDataSource())
        assertEquals(
            "document_part_integrity_failure",
            (repo.inspect(storage.manifest.map { it.copy(sha256 = "0".repeat(64)) })
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(1, scan.closed)
        assertEquals(
            "document_scanner_not_configured",
            (InspectedDocumentRepository(
                        storage,
                        UnavailableDocumentScanDataSource(),
                        TikaDocumentMediaTypeDataSource(),
                    )
                    .inspect(storage.manifest) as Result.Failed)
                .failure
                .code,
        )
        val original = Result.Failed(Failure(FailureKind.FORBIDDEN, "original"))
        assertSame(original, safeDocumentInspectionCall<Unit> { original })
    }
}
