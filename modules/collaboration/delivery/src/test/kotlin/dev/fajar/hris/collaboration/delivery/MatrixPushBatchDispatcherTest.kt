package dev.fajar.hris.collaboration.delivery

import dev.fajar.hris.collaboration.delivery.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixPushBatchDispatcherTest {
    private val company =
        MatrixPushDeviceKey.Company(CollaborationPushKey("hriscp1_" + "a".repeat(64)))
    private val group =
        MatrixPushDeviceKey.Space(CollaborationSpacePushKey("hrisgp1_" + "b".repeat(64)))
    private val event = CollaborationPushEvent("!fixture:matrix.example.test", "$" + "fixture")
    private val correlation = UUID.randomUUID()
    private val clock = MutableClock()

    @Test
    fun bothScopesKeepTypedKeysEventCorrelationAndOneDeadlineWithStableRejectionOrder() {
        val deadline = clock.at.plusSeconds(10)
        var calls = 0
        val adapter =
            MatrixPushBatchDispatcher(
                { hint, id, end ->
                    calls++
                    assertEquals(listOf(company.value), hint.keys.map { it.value })
                    assertEquals(event, hint.event)
                    assertEquals(correlation, id)
                    assertEquals(deadline, end)
                    clock.at = clock.at.plusSeconds(3)
                    Result.Success(CollaborationPushReceipt(hint.keys))
                },
                { hint, id, end ->
                    calls++
                    assertEquals(listOf(group.value), hint.keys.map { it.value })
                    assertEquals(event, hint.event)
                    assertEquals(correlation, id)
                    assertEquals(deadline, end)
                    Result.Success(CollaborationSpacePushReceipt(hint.keys))
                },
                clock,
            )
        val result =
            adapter.accept(MatrixPushBatch(event, listOf(group, company)), correlation, deadline)
                as Result.Success
        assertEquals(listOf(group.value, company.value), result.value.rejected)
        assertEquals(2, calls)
        assertFalse(result.value.toString().contains(group.value))
    }

    @Test
    fun malformedWholeBatchNeverCallsEitherHandler() {
        var calls = 0
        val adapter =
            MatrixPushBatchDispatcher(
                { _, _, _ ->
                    calls++
                    Result.Success(CollaborationPushReceipt(emptyList()))
                },
                { _, _, _ ->
                    calls++
                    Result.Success(CollaborationSpacePushReceipt(emptyList()))
                },
                clock,
            )
        for (batch in
            listOf(
                MatrixPushBatch(null, emptyList()),
                MatrixPushBatch(event, listOf(company, company)),
                MatrixPushBatch(event, List(33) { company }),
                MatrixPushBatch(
                    event,
                    listOf(
                        company,
                        MatrixPushDeviceKey.Space(CollaborationSpacePushKey(company.value)),
                    ),
                ),
                MatrixPushBatch(event.copy(eventId = "invalid"), listOf(company, group)),
            )) assertInstanceOf(
            Result.Failed::class.java,
            adapter.accept(batch, correlation, clock.at.plusSeconds(10)),
        )
        assertEquals(0, calls)
    }

    @Test
    fun companyTimeoutCannotStartGroupWorkOrExtendTheOriginalBudget() {
        var groupCalls = 0
        for (seconds in listOf(-1L, 10L, 11L)) {
            val adapter =
                MatrixPushBatchDispatcher(
                    { _, _, _ ->
                        clock.at = clock.at.plusSeconds(seconds)
                        Result.Success(CollaborationPushReceipt(emptyList()))
                    },
                    { _, _, _ ->
                        groupCalls++
                        Result.Success(CollaborationSpacePushReceipt(emptyList()))
                    },
                    clock,
                )
            assertInstanceOf(
                Result.Failed::class.java,
                adapter.accept(
                    MatrixPushBatch(null, listOf(company, group)),
                    correlation,
                    clock.at.plusSeconds(10),
                ),
            )
        }
        assertEquals(0, groupCalls)
    }

    @Test
    fun temporarilySuppressedGroupPreservesCompanyResultAndSingleScopeNeverCallsTheOther() {
        val adapter =
            MatrixPushBatchDispatcher(
                { hint, _, _ -> Result.Success(CollaborationPushReceipt(hint.keys)) },
                { _, _, _ -> Result.Success(CollaborationSpacePushReceipt(emptyList())) },
                clock,
            )
        val result =
            adapter.accept(
                MatrixPushBatch(event, listOf(company, group)),
                correlation,
                clock.at.plusSeconds(10),
            ) as Result.Success
        assertEquals(listOf(company.value), result.value.rejected)
        val only =
            MatrixPushBatchDispatcher(
                { _, _, _ -> error("No company key") },
                { hint, _, _ -> Result.Success(CollaborationSpacePushReceipt(hint.keys)) },
                clock,
            )
        assertEquals(
            listOf(group.value),
            (only.accept(
                    MatrixPushBatch(null, listOf(group)),
                    correlation,
                    clock.at.plusSeconds(10),
                ) as Result.Success)
                .value
                .rejected,
        )
    }

    @Test
    fun partialFailurePreservesFailureInsteadOfInventingPermanentRetirement() {
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_group_failed"))
        var calls = 0
        val adapter =
            MatrixPushBatchDispatcher(
                { _, _, _ ->
                    calls++
                    Result.Success(CollaborationPushReceipt(emptyList()))
                },
                { _, _, _ -> failure },
                clock,
            )
        repeat(2) {
            assertEquals(
                failure,
                adapter.accept(
                    MatrixPushBatch(null, listOf(company, group)),
                    correlation,
                    clock.at.plusSeconds(10),
                ),
            )
        }
        assertEquals(2, calls)
    }

    @Test
    fun foreignOrDuplicateHandlerRejectionsCannotEscapeTheirInputScope() {
        for (rejected in
            listOf(listOf(CollaborationPushKey(group.value)), listOf(company.key, company.key))) {
            val adapter =
                MatrixPushBatchDispatcher(
                    { _, _, _ -> Result.Success(CollaborationPushReceipt(rejected)) },
                    { _, _, _ -> error("Invalid company response cannot dispatch group") },
                    clock,
                )
            assertInstanceOf(
                Result.Failed::class.java,
                adapter.accept(
                    MatrixPushBatch(null, listOf(company, group)),
                    correlation,
                    clock.at.plusSeconds(10),
                ),
            )
        }
    }

    private class MutableClock(var at: Instant = Instant.parse("2026-11-04T20:00:00Z")) : Clock() {
        override fun instant() = at

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }
}
