package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.errors.DocumentScannerNotConfigured

class UnavailableDocumentScanDataSource : DocumentScanDataSource {
    override fun open(): DocumentScanSession = throw DocumentScannerNotConfigured()
}
