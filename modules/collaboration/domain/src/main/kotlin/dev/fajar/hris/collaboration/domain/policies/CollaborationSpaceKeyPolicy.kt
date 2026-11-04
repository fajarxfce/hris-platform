package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import java.util.UUID

/** Pure metadata validation, not live provider, recipient or private-key possession authority. */
fun validCollaborationSpaceKey(value: CollaborationSpaceKeyPublication): Boolean {
    val account = value.accountId.toString().replace("-", "")
    val space = value.spaceId.toString().replace("-", "")
    val user = value.matrixUserId.substringBefore(':')
    return listOf(value.spaceId, value.accountId, value.deviceId, value.nativeSessionId).none {
        it == UUID(0, 0)
    } &&
        value.matrixUserId.length <= 255 &&
        validCollaborationSpaceServer(value.matrixUserId.substringAfter(':', "")) &&
        (user == "@hrisg_${space}_$account" ||
            user.matches(Regex("@hris_[a-f0-9]{32}_$account"))) &&
        (value.matrixDeviceId == "HRISG_" + value.deviceId.toString().replace("-", "") ||
            value.matrixDeviceId.matches(Regex("HRIS_[a-f0-9]{32}"))) &&
        value.fingerprint.matches(Regex("[a-f0-9]{64}")) &&
        value.version in 0L..1L &&
        (value.version == 1L) == (value.publishedAt != null) &&
        listOfNotNull(value.reservedAt, value.publishedAt).all {
            it.nano % 1000 == 0 && it.epochSecond in -62135596800L..253402300799L
        } &&
        (value.publishedAt?.let { it >= value.reservedAt } != false)
}
