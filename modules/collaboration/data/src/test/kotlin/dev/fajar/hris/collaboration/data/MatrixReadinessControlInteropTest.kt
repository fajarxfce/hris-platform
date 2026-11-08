package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixReadinessControlInteropTest {
    @Test
    fun selfControlWakesExistingSyncAndRetainsOriginalAcrossLostResponseAndBearerRotation() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            var identity = f.login()
            val owner =
                CollaborationSyncOwner(
                    f.request.companyId,
                    f.request.accountId,
                    f.request.deviceId,
                    f.config.serverName,
                )
            val scope =
                (snapshotCollaborationRoomScope(
                        owner.companyId,
                        owner.accountId,
                        owner.serverName,
                        emptyList(),
                    )
                        as Result.Success)
                    .value
            val control =
                CollaborationReadinessControl(
                    UUID.randomUUID(),
                    owner.companyId,
                    owner.accountId,
                    UUID.randomUUID(),
                    owner.deviceId,
                    f.clock.instant().toEpochMilli(),
                )
            fun send() =
                InternalCollaborationMatrixReadinessRepository(
                        HttpMatrixClientDataSource(f.client, f.config, f.clock)
                    )
                    .send(
                        control,
                        owner.serverName,
                        identity.accessToken,
                        f.clock.instant().plusSeconds(10),
                    )
            var replayPosition: CollaborationSyncPosition? = null
            var replayEvents: tools.jackson.databind.JsonNode? = null
            val initialOrigin = f.config.origin
            HttpMatrixSyncDataSource(f.client, f.config, f.clock).use { source ->
                val entered = CountDownLatch(1)
                val watched =
                    object : MatrixSyncDataSource {
                        override fun open(
                            userId: String,
                            deviceId: String,
                            bearer: String,
                            deadline: java.time.Instant,
                        ): MatrixSyncConnection {
                            val connection = source.open(userId, deviceId, bearer, deadline)
                            return object : MatrixSyncConnection by connection {
                                override fun sync(
                                    since: String?,
                                    filterId: String,
                                    timeoutMillis: Int,
                                    fullState: Boolean,
                                ): tools.jackson.databind.JsonNode {
                                    if (timeoutMillis > 0) entered.countDown()
                                    return connection.sync(
                                        since,
                                        filterId,
                                        timeoutMillis,
                                        fullState,
                                    )
                                }
                            }
                        }
                    }
                val receiver =
                    InternalCollaborationMatrixSyncRepository(watched, collaborationCursorFixture())
                fun sync(
                    position: CollaborationSyncPosition?,
                    timeout: Int = 0,
                ): CollaborationMatrixSyncBatch =
                    (receiver.sync(
                            owner,
                            scope,
                            position,
                            timeout,
                            false,
                            identity.accessToken,
                            f.clock.instant().plusSeconds(30),
                        ) as Result.Success)
                        .value
                fun position(batch: CollaborationMatrixSyncBatch) =
                    CollaborationSyncPosition(
                        scope.digest,
                        batch.providerSince,
                        batch.providerFilterId,
                    )
                fun events(batch: CollaborationMatrixSyncBatch) =
                    f.json
                        .readTree(f.json.writeValueAsBytes(batch.payload))
                        .path("to_device")
                        .path("events")
                val before = position(sync(null))
                Executors.newSingleThreadExecutor().use { executor ->
                    val pending =
                        executor.submit<CollaborationMatrixSyncBatch> { sync(before, 25_000) }
                    try {
                        assertTrue(entered.await(5, TimeUnit.SECONDS))
                        assertEquals(Result.Success(Unit), send())
                        val first = pending.get(5, TimeUnit.SECONDS)
                        assertEquals(1, events(first).size())
                        assertEquals(
                            COLLABORATION_READINESS_CONTROL_TYPE,
                            events(first)[0].path("type").asString(),
                        )
                        assertEquals(identity.userId, events(first)[0].path("sender").asString())
                        assertEquals(
                            control.eventId.toString(),
                            events(first)[0].path("content").path("event_id").asString(),
                        )
                        assertEquals(
                            events(first),
                            events(sync(before)),
                            "Unacknowledged device event must remain replayable",
                        )
                        assertTrue(events(sync(position(first))).isEmpty)
                        assertEquals(Result.Success(Unit), send())
                        identity = f.login()
                        assertEquals(Result.Success(Unit), send())
                        val quiet = sync(position(first))
                        assertTrue(
                            events(quiet).isEmpty,
                            "Same transaction on same device must deduplicate",
                        )
                        replayPosition = position(quiet)
                        replayEvents = events(first)
                    } finally {
                        pending.cancel(true)
                        executor.shutdownNow()
                        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
                    }
                }
            }
            // Docker may replace the exposed host port on restart. Reacquire the fixture's
            // raw connection configuration, retaining the same provider identity and cursor.
            f.restart()
            println("readinessControlFixtureOriginChanged=${initialOrigin != f.config.origin}")
            assertEquals(Result.Success(Unit), send())
            HttpMatrixSyncDataSource(f.client, f.config, f.clock).use { source ->
                val receiver =
                    InternalCollaborationMatrixSyncRepository(source, collaborationCursorFixture())
                val replay =
                    (receiver.sync(
                            owner,
                            scope,
                            checkNotNull(replayPosition),
                            0,
                            false,
                            identity.accessToken,
                            f.clock.instant().plusSeconds(10),
                        ) as Result.Success)
                        .value
                val events =
                    f.json
                        .readTree(f.json.writeValueAsBytes(replay.payload))
                        .path("to_device")
                        .path("events")
                assertEquals(
                    checkNotNull(replayEvents),
                    events,
                    "Provider restart may replay; client must durably deduplicate",
                )
            }
        }
    }
}
