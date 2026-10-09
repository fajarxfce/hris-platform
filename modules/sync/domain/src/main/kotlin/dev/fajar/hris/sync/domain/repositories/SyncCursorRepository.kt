package dev.fajar.hris.sync.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.domain.entities.*

interface SyncCursorRepository {
    fun encode(cursor: SyncCursor): Result<String>

    fun decode(value: String): Result<SyncCursor>

    fun fingerprint(scope: SyncScope): Result<String>
}
