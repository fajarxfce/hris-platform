package dev.fajar.hris.sync.data.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.data.crypto.safeSyncCursorCall
import dev.fajar.hris.sync.data.datasources.SyncCursorDataSource
import dev.fajar.hris.sync.data.mappers.*
import dev.fajar.hris.sync.data.models.SyncCursorData
import dev.fajar.hris.sync.domain.entities.*
import dev.fajar.hris.sync.domain.repositories.SyncCursorRepository
import tools.jackson.databind.ObjectMapper

class EncryptedSyncCursorRepository(
    private val source: SyncCursorDataSource,
    private val json: ObjectMapper,
) : SyncCursorRepository {
    override fun encode(cursor: SyncCursor): Result<String> = safeSyncCursorCall {
        val bytes = json.writeValueAsBytes(cursor.toData())
        try {
            source.encrypt(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    override fun decode(value: String): Result<SyncCursor> = safeSyncCursorCall {
        val bytes = source.decrypt(value)
        try {
            json.readValue(bytes, SyncCursorData::class.java).toCursor()
        } finally {
            bytes.fill(0)
        }
    }

    override fun fingerprint(scope: SyncScope): Result<String> = safeSyncCursorCall {
        syncScopeFingerprint(scope)
    }
}
