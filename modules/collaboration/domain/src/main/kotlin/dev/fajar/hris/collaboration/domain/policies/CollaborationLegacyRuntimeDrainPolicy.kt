package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Duration
import java.time.Instant

/** Slots are bounded metadata. Final publication must independently check actual database time. */
fun collaborationLegacyRuntimeDrainStatus(
    runtime: CollaborationLegacyRuntime,
    owner: CollaborationLegacyRuntimeOwner,
    target: CollaborationLegacyRuntimeTarget,
    slots: List<CollaborationLegacyRuntimeLease>,
    at: Instant,
): Result<CollaborationLegacyRuntimeDrainStatus> =
    collaborationLegacyFreezeAction(runtime, owner, target, at).flatMap { action ->
        if (
            action != CollaborationLegacyFreezeAction.REPLAY ||
                slots.size > 8 ||
                slots.map { it.slot }.toSet().size != slots.size ||
                slots.map { it.token }.toSet().size != slots.size ||
                slots.any {
                    it.owner != owner ||
                        it.runtimeVersion != 0L ||
                        it.slot !in 0..7 ||
                        it.startedAt < runtime.createdAt ||
                        it.startedAt > at ||
                        it.expiresAt <= it.startedAt ||
                        Duration.between(it.startedAt, it.expiresAt) > Duration.ofSeconds(60) ||
                        (it.releasedAt != null &&
                            (it.releasedAt < it.startedAt || it.releasedAt > at))
                }
        )
            return@flatMap Result.Failed(
                Failure(FailureKind.UNAVAILABLE, "collaboration_runtime_changed")
            )
        // DB and application timestamps have no ordered cross-clock relation at the freeze.
        // An in-flight claim may legitimately have startedAt later than runtime.frozenAt.
        val active = slots.filter { it.releasedAt == null && it.expiresAt > at }
        Result.Success(
            CollaborationLegacyRuntimeDrainStatus(
                target,
                runtime.version,
                active.size,
                active.maxOfOrNull { it.expiresAt },
                at,
            )
        )
    }
