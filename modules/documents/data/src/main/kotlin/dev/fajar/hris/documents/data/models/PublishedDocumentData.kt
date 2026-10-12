package dev.fajar.hris.documents.data.models

import dev.fajar.hris.schema.tables.records.DocumentRevisionsRecord
import dev.fajar.hris.schema.tables.records.DocumentsRecord

data class PublishedDocumentData(
    val document: DocumentsRecord,
    val revision: DocumentRevisionsRecord,
)
