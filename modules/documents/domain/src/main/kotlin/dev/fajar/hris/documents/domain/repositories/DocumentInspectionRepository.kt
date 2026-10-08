package dev.fajar.hris.documents.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.documents.domain.entities.*

interface DocumentInspectionRepository {
    fun inspect(parts: List<DocumentContentPart>): Result<DocumentInspection>
}
