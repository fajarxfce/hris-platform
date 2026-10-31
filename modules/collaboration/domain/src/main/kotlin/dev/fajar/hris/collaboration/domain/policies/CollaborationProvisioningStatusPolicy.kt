package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.*

const val COLLABORATION_PROVISIONING_RETRY_SECONDS = 60L

/** The pointer and immutable job must identify the currently bound company/account/device. */
fun collaborationProvisioningMatches(
    device: CollaborationDevice,
    pointer: CollaborationProvisioning,
    job: BackgroundJob,
): Boolean {
    val request = job.request
    val parsed = nativeCollaborationProvisioningRequest(request)
    if (parsed !is Result.Success) return false
    val input = parsed.value
    return pointer.companyId == device.companyId &&
        pointer.accountId == device.accountId &&
        pointer.deviceId == device.id &&
        pointer.jobId == request.id &&
        request.companyId == device.companyId &&
        request.actorId == device.accountId &&
        input.deviceId == device.id &&
        input.parentId == device.nativeSessionId &&
        input.platform == device.platform &&
        request.createdAt == pointer.updatedAt &&
        pointer.createdAt >= device.createdAt &&
        pointer.updatedAt >= pointer.createdAt &&
        pointer.version in 0..999999
}

fun collaborationProvisioningStatus(
    device: CollaborationDevice,
    pointer: CollaborationProvisioning,
    job: BackgroundJob,
) =
    CollaborationProvisioningStatus(
        pointer.version,
        job.request.id,
        job.status,
        job.attempts,
        job.failureCode,
        if (
            device.state == CollaborationDeviceState.PROVISIONING &&
                job.status in setOf(JobStatus.FAILED, JobStatus.CANCELLED) &&
                pointer.version < 999999
        )
            pointer.updatedAt.plusSeconds(COLLABORATION_PROVISIONING_RETRY_SECONDS)
        else null,
    )
