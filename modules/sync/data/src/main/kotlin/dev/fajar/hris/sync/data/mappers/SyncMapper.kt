package dev.fajar.hris.sync.data.mappers

import dev.fajar.hris.core.database.commandFingerprint
import dev.fajar.hris.sync.data.crypto.InvalidSyncCursorException
import dev.fajar.hris.sync.data.models.*
import dev.fajar.hris.sync.domain.entities.*
import java.time.Instant

fun SyncScope.toSelection() =
    SyncSelection(companyId, accountId, employmentIds, collections.map { it.name }.toSet())

fun SyncResourceRow.toResource() =
    SyncResource(SyncResourceKey(SyncCollection.valueOf(collection), id), version)

fun syncScopeFingerprint(scope: SyncScope): String =
    commandFingerprint(
        listOf(
            "mobile-sync:1",
            scope.accountId.toString(),
            scope.companyId.toString(),
            scope.credentialVersion.toString(),
            scope.membershipVersion.toString(),
            scope.companyVersion.toString(),
            scope.permissions.size.toString(),
        ) +
            scope.permissions.sorted() +
            listOf(scope.employmentIds.size.toString()) +
            scope.employmentIds.map { it.toString() }.sorted() +
            scope.collections.map { it.name }.sorted()
    )

fun SyncCursor.toData() =
    SyncCursorData(
        1,
        accountId,
        companyId,
        epoch,
        scopeFingerprint,
        phase.name,
        position,
        issuedAt.epochSecond,
        expiresAt.epochSecond,
        upperPosition,
        after?.collection?.name,
        after?.id,
        page,
    )

fun SyncCursorData.toCursor(): SyncCursor {
    if (
        schema != 1 ||
            !fingerprint.matches(Regex("[0-9a-f]{64}")) ||
            (afterCollection == null) != (afterId == null)
    )
        throw InvalidSyncCursorException()
    return SyncCursor(
        accountId,
        companyId,
        epoch,
        fingerprint,
        SyncCursorPhase.valueOf(phase),
        position,
        Instant.ofEpochSecond(issuedAt),
        Instant.ofEpochSecond(expiresAt),
        upperPosition,
        afterCollection?.let {
            SyncResourceKey(SyncCollection.valueOf(it), requireNotNull(afterId))
        },
        page,
    )
}
