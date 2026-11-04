package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.legacyRuntimeCompletionFromRow
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationLegacyRuntimeCompletionRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeCompletionsRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException

class CollaborationLegacyRuntimeCompletionMappingTest {
    private val owner =
        CollaborationLegacyRuntimeOwner(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
        )
    private val target = CollaborationLegacyRuntimeTarget(UUID.randomUUID(), UUID.randomUUID())
    private val gateway = UUID.randomUUID()
    private val at = Instant.parse("2026-11-04T02:40:00Z")
    private val request =
        CompleteCollaborationLegacyRuntimeRequest(
            owner,
            target,
            3,
            1,
            0,
            2,
            1,
            0,
            0,
            "a".repeat(64),
            at,
        )

    @Test
    fun mappingRetainsOriginalAndTargetCoordinatesAndTerminalRetirementVersions() {
        val mapped = legacyRuntimeCompletionFromRow(row(), owner)
        assertEquals(owner, mapped.owner)
        assertEquals(target, mapped.target)
        assertEquals(CollaborationLegacyGatewayRetirement(gateway, 1), mapped.sourceGateway)
        assertEquals(2, mapped.sourceDeviceVersion)
        assertEquals(1, mapped.targetDeviceVersion)
        assertEquals(at, mapped.keysObservedAt)
        val exhausted =
            row().apply {
                sourceDeviceVersion = 1000000
                sourceProviderGeneration = 1000000
                sourceGatewayVersion = 1000000
                targetDeviceVersion = 999999
                targetProviderGeneration = 999999
            }
        assertEquals(1000000, legacyRuntimeCompletionFromRow(exhausted, owner).sourceDeviceVersion)
        assertNull(
            legacyRuntimeCompletionFromRow(
                    row().apply {
                        sourceGatewayId = null
                        sourceGatewayVersion = null
                    },
                    owner,
                )
                .sourceGateway
        )
    }

    @Test
    fun foreignMalformedAndIncoherentCompletionRowsNeverEscapeTheSafeBoundary() {
        val changes =
            listOf<(CollaborationLegacyRuntimeCompletionsRecord) -> Unit>(
                { it.legacyCompanyId = UUID.randomUUID() },
                { it.legacyDeviceId = UUID.randomUUID() },
                { it.accountId = UUID.randomUUID() },
                { it.nativeSessionId = UUID.randomUUID() },
                { it.spaceId = null },
                { it.deviceId = null },
                { it.matrixUserId = "@other:fixture.test" },
                { it.matrixDeviceId = "HRISG_other" },
                { it.fingerprint = "b".repeat(63) },
                { it.nativeSessionVersion = -1 },
                { it.provisioningRevision = 1000000 },
                { it.sourceDeviceVersion = 0 },
                { it.sourceProviderGeneration = 1000001 },
                { it.sourceGatewayId = null },
                { it.sourceGatewayVersion = null },
                { it.sourceGatewayVersion = 0 },
                { it.targetDeviceVersion = 1000000 },
                { it.targetProviderGeneration = 1000000 },
                { it.frozenAt = at.plusSeconds(1).atOffset(UTC) },
                { it.completedAt = at.minusSeconds(1).atOffset(UTC) },
                { it.keysObservedAt = at.plusNanos(1).atOffset(UTC) },
                { it.completedAt = null },
            )
        for (change in changes) {
            val source = Source().apply { answer = row().also(change) }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationLegacyRuntimeCompletionRepository(source).find(owner),
            )
        }
        assertNull(
            success(
                StoredCollaborationLegacyRuntimeCompletionRepository(
                        Source().apply { answer = null }
                    )
                    .find(owner)
            )
        )
    }

    @Test
    fun completionRequiresItsExactSuccessorMappingAndRejectsInvalidInputsBeforeIo() {
        val valid = Source()
        assertEquals(
            legacyRuntimeCompletionFromRow(row(), owner),
            success(StoredCollaborationLegacyRuntimeCompletionRepository(valid).complete(request)),
        )
        assertEquals(1, valid.completions)
        assertEquals(
            request.keysObservedAt.atOffset(UTC),
            requireNotNull(valid.input).keysObservedAt,
        )
        for (change in
            listOf<(CollaborationLegacyRuntimeCompletionsRecord) -> Unit>(
                { it.spaceId = UUID.randomUUID() },
                { it.deviceId = UUID.randomUUID() },
                { it.nativeSessionVersion = 4 },
                { it.provisioningRevision = 3 },
                { it.sourceDeviceVersion = 3 },
                { it.sourceProviderGeneration = 2 },
                { it.sourceGatewayVersion = 2 },
                { it.targetDeviceVersion = 2 },
                { it.targetProviderGeneration = 1 },
                { it.fingerprint = "b".repeat(64) },
                { it.keysObservedAt = at.minusSeconds(1).atOffset(UTC) },
            )) {
            val source = Source().apply { answer = row().also(change) }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationLegacyRuntimeCompletionRepository(source).complete(request),
            )
            assertEquals(1, source.completions)
        }
        assertInstanceOf(
            Result.Failed::class.java,
            StoredCollaborationLegacyRuntimeCompletionRepository(Source().apply { answer = null })
                .complete(request),
        )
        for (invalid in
            listOf(
                request.copy(nativeSessionVersion = -1),
                request.copy(targetDeviceVersion = 0),
                request.copy(targetProviderGeneration = 1000000),
                request.copy(provisioningRevision = -1),
                request.copy(legacyDeviceVersion = 1000000),
                request.copy(legacyProviderGeneration = -1),
                request.copy(legacyGatewayVersion = 1000000),
                request.copy(fingerprint = "A".repeat(64)),
                request.copy(keysObservedAt = at.plusNanos(1)),
            )) {
            val source = Source()
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationLegacyRuntimeCompletionRepository(source).complete(invalid),
            )
            assertEquals(0, source.completions)
        }
    }

    @Test
    fun acquisitionFailuresAndOriginalCancellationPropagateWithoutAnotherCompletionAttempt() {
        for (after in listOf(false, true)) for (mode in
            listOf("timeout", "cancel", "interrupt", "flag")) {
            val cancel = CancellationException("Owned completion cancellation")
            val interrupt = InterruptedException("Owned completion interruption")
            fun fail() {
                when (mode) {
                    "timeout" -> throw QueryTimeoutException("Owned fixture timeout")
                    "cancel" -> throw cancel
                    "interrupt" -> throw interrupt
                    else -> Thread.currentThread().interrupt()
                }
            }
            val source =
                object : Source() {
                    override fun complete(
                        request: CompleteCollaborationLegacyRuntimeData
                    ): Boolean {
                        val done = super.complete(request)
                        if (!after) fail()
                        return done
                    }

                    override fun find(
                        owner: CollaborationLegacyRuntimeCoordinatesData
                    ): CollaborationLegacyRuntimeCompletionsRecord? {
                        if (after) fail()
                        return super.find(owner)
                    }
                }
            val repository = StoredCollaborationLegacyRuntimeCompletionRepository(source)
            when (mode) {
                "timeout" ->
                    assertEquals(
                        "database_busy",
                        (repository.complete(request) as Result.Failed).failure.code,
                    )
                "cancel" ->
                    assertSame(
                        cancel,
                        assertThrows(CancellationException::class.java) {
                            repository.complete(request)
                        },
                    )
                "interrupt" ->
                    assertSame(
                        interrupt,
                        assertThrows(InterruptedException::class.java) {
                            repository.complete(request)
                        },
                    )
                else ->
                    try {
                        assertThrows(InterruptedException::class.java) {
                            repository.complete(request)
                        }
                        assertTrue(Thread.currentThread().isInterrupted)
                    } finally {
                        Thread.interrupted()
                    }
            }
            assertEquals(1, source.completions)
        }
    }

    private open inner class Source : CollaborationLegacyRuntimeCompletionDataSource {
        var answer: CollaborationLegacyRuntimeCompletionsRecord? = row()
        var input: CompleteCollaborationLegacyRuntimeData? = null
        var completions = 0

        override fun find(
            owner: CollaborationLegacyRuntimeCoordinatesData
        ): CollaborationLegacyRuntimeCompletionsRecord? = answer

        override fun complete(request: CompleteCollaborationLegacyRuntimeData): Boolean {
            input = request
            completions++
            return true
        }
    }

    private fun row() =
        CollaborationLegacyRuntimeCompletionsRecord().apply {
            legacyCompanyId = owner.companyId
            legacyDeviceId = owner.deviceId
            accountId = owner.accountId
            nativeSessionId = owner.nativeSessionId
            spaceId = target.spaceId
            deviceId = target.deviceId
            matrixUserId =
                "@${collaborationMatrixLocalpart(owner.companyId,owner.accountId)}:fixture.test"
            matrixDeviceId = collaborationMatrixDeviceId(owner.deviceId)
            fingerprint = request.fingerprint
            nativeSessionVersion = request.nativeSessionVersion
            provisioningRevision = request.provisioningRevision
            sourceDeviceVersion = 2
            sourceProviderGeneration = 1
            sourceGatewayId = gateway
            sourceGatewayVersion = 1
            targetDeviceVersion = request.targetDeviceVersion
            targetProviderGeneration = request.targetProviderGeneration
            frozenAt = at.minusSeconds(2).atOffset(UTC)
            keysObservedAt = at.atOffset(UTC)
            completedAt = at.plusSeconds(1).atOffset(UTC)
        }

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }
}
