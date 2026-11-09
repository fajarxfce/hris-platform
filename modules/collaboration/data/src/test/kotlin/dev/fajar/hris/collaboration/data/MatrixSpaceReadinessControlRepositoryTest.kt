package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationSpaceMatrixReadinessRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class MatrixSpaceReadinessControlRepositoryTest {
    private val json = JsonMapper.builder().build()
    private val deadline = Instant.parse("2026-11-09T18:00:20Z")

    private fun control(owner: CollaborationSpaceHistoryOwner) =
        CollaborationSpaceReadinessControl(
            UUID.randomUUID(),
            owner.spaceId,
            owner.accountId,
            UUID.randomUUID(),
            owner.deviceId,
            deadline.minusSeconds(20).toEpochMilli(),
            Long.MAX_VALUE,
        )

    @Test
    fun routesExactLogicalDeviceToRetainedFreshAndFreshDeviceWithOriginalUser() {
        val retained = SpaceRoomHistoryFixture(legacy = true).owner
        val fresh = SpaceRoomHistoryFixture().owner
        val newOriginal =
            retained.copy(
                legacy = null,
                matrixDeviceId = "HRISG_${retained.deviceId.toString().replace("-", "")}",
            )
        for (owner in listOf(retained, fresh, newOriginal)) {
            val hint = control(owner)
            val source = Source()
            val repository = InternalCollaborationSpaceMatrixReadinessRepository(source)
            assertEquals(
                Result.Success(Unit),
                repository.send(hint, owner, "fixture-token", deadline),
            )
            assertEquals(1, source.calls)
            assertEquals("hris_space_ready_${hint.eventId}_${owner.deviceId}", source.transaction)
            assertEquals(
                mapOf(
                    "messages" to
                        mapOf(
                            owner.matrixUserId to
                                mapOf(
                                    owner.matrixDeviceId to
                                        collaborationSpaceReadinessControlContent(hint)
                                )
                        )
                ),
                source.body,
            )
            assertEquals(deadline, source.deadline)
            assertEquals("fixture-token", source.token)
            assertFalse(source.body.toString().contains("company_id"))
        }
    }

    @Test
    fun foreignLogicalCoordinatesAndInventedPhysicalIdentitiesNeverAcquireTheProvider() {
        val owner = SpaceRoomHistoryFixture(legacy = true).owner
        val hint = control(owner)
        val source = Source()
        val repository = InternalCollaborationSpaceMatrixReadinessRepository(source)
        for (foreign in
            listOf(
                hint.copy(spaceId = UUID.randomUUID()),
                hint.copy(accountId = UUID.randomUUID()),
                hint.copy(deviceId = UUID.randomUUID()),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                repository.send(foreign, owner, "fixture-token", deadline),
            )
        }
        for (foreign in
            listOf(
                owner.copy(matrixUserId = "@alien:example.test"),
                owner.copy(matrixDeviceId = "OTHER"),
                owner.copy(legacy = null),
                owner.copy(spaceId = UUID(0, 0)),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                repository.send(hint, foreign, "fixture-token", deadline),
            )
        }
        assertEquals(0, source.calls)
    }

    @Test
    fun malformedAcknowledgementAndProviderOutcomesRemainUncertainWithoutRetry() {
        val owner = SpaceRoomHistoryFixture(legacy = true).owner
        val hint = control(owner)
        for (response in listOf("null", "[]", "false", "{\"extra\":true}")) {
            val source = Source().also { it.response = json.readTree(response) }
            val outcome =
                InternalCollaborationSpaceMatrixReadinessRepository(source)
                    .send(hint, owner, "fixture-token", deadline)
            assertEquals(
                "collaboration_provider_invalid_response",
                (outcome as Result.Failed).failure.code,
            )
            assertEquals(1, source.calls)
        }
        for (status in listOf(403, 429, 503)) {
            val source = Source().also { it.failure = MatrixStatusException(status) }
            assertInstanceOf(
                Result.Failed::class.java,
                InternalCollaborationSpaceMatrixReadinessRepository(source)
                    .send(hint, owner, "fixture-token", deadline),
            )
            assertEquals(1, source.calls)
        }
        for (failure in listOf(CancellationException("fixture"), InterruptedException("fixture"))) {
            val source = Source().also { it.failure = failure }
            val repository = InternalCollaborationSpaceMatrixReadinessRepository(source)
            assertSame(
                failure,
                assertThrows(failure.javaClass) {
                    repository.send(hint, owner, "fixture-token", deadline)
                },
            )
            assertEquals(1, source.calls)
        }
    }

    private inner class Source : MatrixSpaceReadinessControlDataSource {
        var calls = 0
        var transaction: String? = null
        var body: Map<String, Any>? = null
        var token: String? = null
        var deadline: Instant? = null
        var response = json.readTree("{}")
        var failure: Exception? = null

        override fun sendSpaceReadinessControl(
            transactionId: String,
            body: Map<String, Any>,
            token: String,
            deadline: Instant,
        ): JsonNode {
            calls++
            this.transaction = transactionId
            this.body = body
            this.token = token
            this.deadline = deadline
            failure?.let { throw it }
            return response
        }
    }
}
