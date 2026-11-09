package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import java.time.Duration
import java.time.Instant

/** A quiet queue handoff grants no FCM delivery authority and carries no room/message content. */
fun collaborationSpaceReadinessPushLifetime(
    checkpoint: CollaborationSpaceReadinessCheckpoint,
    admission: CollaborationSpaceReadinessAdmission,
    binding: CollaborationSpacePushBinding?,
    now: Instant,
): Int? {
    val lease = checkpoint.lease
    if (
        !validCollaborationSpaceReadinessCheckpoint(checkpoint) ||
            lease.channel != CollaborationSpaceReadinessChannel.PUSH ||
            binding == null ||
            !validCollaborationSpacePushBinding(binding) ||
            binding.version != 1L ||
            binding.publishedAt == null ||
            binding.publishedAt > checkpoint.capturedAt ||
            admission.observedAt > now ||
            admission.expiresAt <= now ||
            lease.until <= now ||
            binding.spaceId != lease.spaceId ||
            binding.spaceId != admission.owner.spaceId ||
            binding.accountId != admission.owner.accountId ||
            binding.deviceId != admission.owner.deviceId ||
            binding.nativeSessionId != admission.keys.nativeSessionId ||
            binding.matrixUserId != admission.owner.matrixUserId ||
            binding.matrixDeviceId != admission.owner.matrixDeviceId
    )
        return null
    return Duration.between(now, checkpoint.expiresAt)
        .seconds
        .coerceAtMost(300)
        .takeIf { it >= 1 }
        ?.toInt()
}
