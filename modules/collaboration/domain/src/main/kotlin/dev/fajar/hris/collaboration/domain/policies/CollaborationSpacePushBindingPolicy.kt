package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpacePushBinding
import java.util.UUID

/** Immutable metadata shape only. Current native, group, provider and room checks are separate. */
fun validCollaborationSpacePushBinding(value: CollaborationSpacePushBinding): Boolean {
    val account = value.accountId.toString().replace("-", "")
    val user = value.matrixUserId.substringBefore(':')
    val group = user == "@${collaborationSpaceMatrixLocalpart(value.spaceId, value.accountId)}"
    return listOf(value.spaceId, value.accountId, value.deviceId, value.nativeSessionId).none {
        it == UUID(0, 0)
    } &&
        value.matrixUserId.length <= 255 &&
        validCollaborationSpaceServer(value.matrixUserId.substringAfter(':', "")) &&
        (group || user.matches(Regex("@hris_[a-f0-9]{32}_$account"))) &&
        (value.matrixDeviceId == "HRISG_" + value.deviceId.toString().replace("-", "") ||
            (!group && value.matrixDeviceId.matches(Regex("HRIS_[a-f0-9]{32}")))) &&
        value.version in 0L..1L &&
        (value.version == 1L) == (value.publishedAt != null) &&
        listOfNotNull(value.reservedAt, value.publishedAt).all {
            it.nano % 1000 == 0 && it.epochSecond in -62135596800L..253402300799L
        } &&
        (value.publishedAt?.let { it >= value.reservedAt } != false)
}

fun validCollaborationSpacePushKey(value: String): Boolean =
    value.matches(Regex("hrisgp1_[a-f0-9]{64}"))
