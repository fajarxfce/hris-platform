package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRecipientDevice
import java.util.UUID

/** Shape validation grants neither recipient authority nor trust in returned provider keys. */
fun validCollaborationSpaceRecipient(value: CollaborationSpaceRecipientDevice): Boolean {
    val account = value.accountId.toString().replace("-", "")
    val device = value.deviceId.toString().replace("-", "")
    val user = value.matrixUserId.substringBefore(':')
    val groupUser = user == "@${collaborationSpaceMatrixLocalpart(value.spaceId, value.accountId)}"
    return listOf(value.spaceId, value.accountId, value.deviceId).none { it == UUID(0, 0) } &&
        validCollaborationSpaceServer(value.serverName) &&
        value.matrixUserId.length <= 255 &&
        value.matrixUserId.substringAfter(':', "") == value.serverName &&
        (groupUser || user.matches(Regex("@hris_[a-f0-9]{32}_$account"))) &&
        (value.matrixDeviceId == "HRISG_$device" ||
            (!groupUser && value.matrixDeviceId.matches(Regex("HRIS_[a-f0-9]{32}")))) &&
        value.fingerprint.matches(Regex("[a-f0-9]{64}")) &&
        value.receiveUntil.nano % 1000 == 0 &&
        value.receiveUntil.epochSecond in -62135596800L..253402300799L
}
