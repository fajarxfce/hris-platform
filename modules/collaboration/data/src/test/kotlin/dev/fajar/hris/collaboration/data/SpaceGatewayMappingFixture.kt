package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceGatewaySessionDataSource
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationSpaceGatewaySessionRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationSpaceGatewaySessionsRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

internal class SpaceGatewayMappingFixture {
    val at = Instant.parse("2026-11-04T00:00:00.123456Z")
    val session =
        CollaborationSpaceGatewaySession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            CollaborationPlatform.ANDROID,
            5,
            0,
            at,
            at,
            at.plusSeconds(3600),
            at.plusSeconds(900),
        )
    val operation = UUID.randomUUID()
    val key = Base64.getEncoder().encodeToString(ByteArray(32) { 73 })
    val crypto = JceIdentityTokenDataSource(parseIdentityKeyring("fixture", "fixture:$key"))
    val source = SpaceGatewayMemorySource()
    val repo = EncryptedCollaborationSpaceGatewaySessionRepository(source, crypto)

    fun create() = success(repo.create(session, operation, at.plusSeconds(30)))

    fun read() = repo.find(session.spaceId, session.accountId, session.deviceId)

    fun <T> success(value: Result<T>): T = (value as Result.Success).value
}

internal class SpaceGatewayMemorySource : CollaborationSpaceGatewaySessionDataSource {
    var row: CollaborationSpaceGatewaySessionsRecord? = null
    var failure: Throwable? = null
    var afterWrite: () -> Unit = {}
    var reads = 0
    var writes = 0
    var forgedLookup = false
    var cleanupCount = 0

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationSpaceGatewaySessionsRecord? {
        reads++
        failure?.let { throw it }
        return row
    }

    override fun byAccess(hash: String) = row?.takeIf { forgedLookup || it.accessHash == hash }

    override fun byRefresh(hash: String) =
        row?.takeIf { forgedLookup || it.refreshHash == hash || it.previousRefreshHash == hash }

    override fun insert(record: CollaborationSpaceGatewaySessionsRecord) {
        writes++
        check(row == null)
        row = copySpaceGateway(record)
        afterWrite()
    }

    override fun replace(
        record: CollaborationSpaceGatewaySessionsRecord,
        expectedVersion: Long,
        expectedRefreshHash: String,
    ): Boolean {
        if (row?.version != expectedVersion || row?.refreshHash != expectedRefreshHash) return false
        writes++
        row = copySpaceGateway(record)
        afterWrite()
        return true
    }

    override fun retire(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: OffsetDateTime,
    ): Boolean {
        val old = row ?: return false
        if (old.version != expectedVersion || old.revokedAt != null) return false
        writes++
        row =
            copySpaceGateway(old).apply {
                version = expectedVersion + 1
                revokedAt = at
                accessHash = null
                refreshHash = null
                replayKind = null
                bootstrapOperationId = null
                previousRefreshHash = null
                replayUntil = null
                replayEncrypted = null
            }
        afterWrite()
        return true
    }

    override fun clearExpiredReplays(at: OffsetDateTime, limit: Int): Int = cleanupCount
}

internal fun copySpaceGateway(row: CollaborationSpaceGatewaySessionsRecord) =
    CollaborationSpaceGatewaySessionsRecord().also { it.from(row) }
