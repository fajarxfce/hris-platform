package dev.fajar.hris.documents.data.datasources
interface DocumentMediaTypeDataSource {
    fun detect(prefix: ByteArray): String
}
