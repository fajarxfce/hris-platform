package dev.fajar.hris.sync.data.datasources

interface SyncCursorDataSource {
    fun encrypt(bytes: ByteArray): String

    fun decrypt(token: String): ByteArray
}
