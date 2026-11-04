package dev.fajar.hris.collaboration.delivery.matrix

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationPushEvent
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpacePushKey
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** The provider shares one app profile; each opaque capability retains its own domain scope. */
sealed interface MatrixPushDeviceKey {
    val value: String

    class Company(val key: CollaborationPushKey) : MatrixPushDeviceKey {
        override val value
            get() = key.value

        override fun toString() = "MatrixPushDeviceKey.Company(<redacted>)"
    }

    class Space(val key: CollaborationSpacePushKey) : MatrixPushDeviceKey {
        override val value
            get() = key.value

        override fun toString() = "MatrixPushDeviceKey.Space(<redacted>)"
    }
}

class MatrixPushBatch(val event: CollaborationPushEvent?, keys: List<MatrixPushDeviceKey>) {
    val keys = keys.toList()

    override fun toString() = "MatrixPushBatch(<redacted>)"
}

class MatrixPushBatchReceipt(rejected: List<String>) {
    val rejected = rejected.toList()

    override fun toString() = "MatrixPushBatchReceipt(<redacted>)"
}

/** Delivery adapter, not a use case. Both independent domain handlers share one original budget. */
class MatrixPushBatchDispatcher(
    private val company: (CollaborationPushHint, UUID, Instant) -> Result<CollaborationPushReceipt>,
    private val space:
        (CollaborationSpacePushHint, UUID, Instant) -> Result<CollaborationSpacePushReceipt>,
    private val clock: Clock,
) {
    fun accept(
        batch: MatrixPushBatch,
        correlationId: UUID,
        deadline: Instant,
    ): Result<MatrixPushBatchReceipt> {
        val start = clock.instant()
        if (deadline <= start || deadline > start.plusSeconds(10)) return unavailable()
        if (
            batch.keys.size !in 1..MATRIX_PUSH_MAXIMUM_DEVICES ||
                batch.keys.map { it.value }.toSet().size != batch.keys.size ||
                batch.keys.any {
                    when (it) {
                        is MatrixPushDeviceKey.Company ->
                            !it.value.matches(Regex("hriscp1_[a-f0-9]{64}"))
                        is MatrixPushDeviceKey.Space -> !validCollaborationSpacePushKey(it.value)
                    }
                } ||
                batch.event?.let { !validCollaborationPushEvent(it) } == true
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_push"))
        fun current() = clock.instant().let { it >= start && it < deadline }
        val rejected = mutableSetOf<String>()
        val companyKeys = batch.keys.filterIsInstance<MatrixPushDeviceKey.Company>().map { it.key }
        val spaceKeys = batch.keys.filterIsInstance<MatrixPushDeviceKey.Space>().map { it.key }
        if (companyKeys.isNotEmpty()) {
            if (!current()) return unavailable()
            val result =
                company(CollaborationPushHint(batch.event, companyKeys), correlationId, deadline)
            if (!current()) return unavailable()
            if (result is Result.Failed) return result
            val values = (result as Result.Success).value.rejected.map { it.value }
            if (
                values.size != values.toSet().size ||
                    values.any { v -> companyKeys.none { it.value == v } }
            )
                return unavailable()
            rejected.addAll(values)
        }
        if (spaceKeys.isNotEmpty()) {
            if (!current()) return unavailable()
            val result =
                space(CollaborationSpacePushHint(batch.event, spaceKeys), correlationId, deadline)
            if (!current()) return unavailable()
            if (result is Result.Failed) return result
            val values = (result as Result.Success).value.rejected.map { it.value }
            if (
                values.size != values.toSet().size ||
                    values.any { v -> spaceKeys.none { it.value == v } }
            )
                return unavailable()
            rejected.addAll(values)
        }
        return if (current())
            Result.Success(
                MatrixPushBatchReceipt(batch.keys.map { it.value }.filter { it in rejected })
            )
        else unavailable()
    }

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_push_unavailable"))
}
