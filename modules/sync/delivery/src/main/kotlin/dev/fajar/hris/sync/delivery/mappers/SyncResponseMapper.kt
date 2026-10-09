package dev.fajar.hris.sync.delivery.mappers

import dev.fajar.hris.sync.delivery.responses.*
import dev.fajar.hris.sync.domain.entities.*

fun SyncBootstrapPage.toResponse() =
    SyncBootstrapResponse(
        collections.map { it.name }.sorted(),
        items.map { SyncResourceResponse(it.key.collection.name, it.key.id, it.version) },
        nextCursor,
        changesCursor,
        serverTime,
    )

fun SyncChangePage.toResponse() =
    SyncChangesResponse(
        items.map {
            SyncChangeResponse(
                it.operation.name,
                it.resource.key.collection.name,
                it.resource.key.id,
                it.resource.version,
            )
        },
        cursor,
        hasMore,
        pendingPublication,
        pollAfterSeconds,
        serverTime,
    )
