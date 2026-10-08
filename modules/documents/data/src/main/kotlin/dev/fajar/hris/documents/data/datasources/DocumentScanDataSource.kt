package dev.fajar.hris.documents.data.datasources
interface DocumentScanDataSource {
    fun open(): DocumentScanSession
}
