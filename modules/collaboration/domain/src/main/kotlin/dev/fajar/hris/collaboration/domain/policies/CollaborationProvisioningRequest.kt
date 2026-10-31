package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationPlatform
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID

data class NativeCollaborationProvisioningRequest(
    val deviceId: UUID,
    val parentId: UUID,
    val platform: CollaborationPlatform,
    val client: ClientVersion?,
)

fun nativeCollaborationProvisioningRequest(
    request: JobRequest
): Result<NativeCollaborationProvisioningRequest> {
    if (
        request.kind != JobKind.COLLABORATION_PROVISION ||
            request.totalItems != 1 ||
            request.progressMode != JobProgressMode.FIXED_TOTAL
    )
        return invalidProvisioningRequest()
    val values = request.values
    if (
        values.keys != setOf("deviceId", "nativeSessionId", "platform") &&
            values.keys != setOf("deviceId", "nativeSessionId", "platform", "clientBuild")
    )
        return invalidProvisioningRequest()
    val device = canonicalUuid(values["deviceId"]) ?: return invalidProvisioningRequest()
    val parent = canonicalUuid(values["nativeSessionId"]) ?: return invalidProvisioningRequest()
    val platform =
        when (values["platform"]) {
            "ANDROID" -> CollaborationPlatform.ANDROID
            "IOS" -> CollaborationPlatform.IOS
            else -> return invalidProvisioningRequest()
        }
    val version =
        values["clientBuild"]?.let {
            val number = it.toIntOrNull()
            if (number == null || number < 1 || number.toString() != it)
                return invalidProvisioningRequest()
            ClientVersion(ClientPlatform.valueOf(platform.name), number)
        }
    return Result.Success(NativeCollaborationProvisioningRequest(device, parent, platform, version))
}

private fun canonicalUuid(value: String?): UUID? {
    if (value == null || value.length != 36) return null
    return try {
        UUID.fromString(value).takeIf { it.toString() == value }
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun invalidProvisioningRequest() =
    Result.Failed(Failure(FailureKind.UNEXPECTED, "invalid_collaboration_provisioning"))
