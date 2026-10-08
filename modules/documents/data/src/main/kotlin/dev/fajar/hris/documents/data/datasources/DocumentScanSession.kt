package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.models.DocumentScanData

interface DocumentScanSession : AutoCloseable {
    fun write(bytes: ByteArray)

    fun finish(): DocumentScanData
}
