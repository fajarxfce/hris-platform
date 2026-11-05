package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result

interface CollaborationSummaryCursorRepository {
    fun seal(cursor: CollaborationSummaryCursor): Result<String>

    fun open(owner: CollaborationSummaryOwner, token: String): Result<CollaborationSummaryCursor>
}
