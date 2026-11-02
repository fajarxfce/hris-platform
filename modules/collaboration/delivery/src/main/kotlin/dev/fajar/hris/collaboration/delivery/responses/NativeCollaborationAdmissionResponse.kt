package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationAdmission
import java.time.Instant

/** A current observation; every Matrix request still enforces its own admission. */
data class NativeCollaborationAdmissionResponse(
    val companyId: String,
    val accountId: String,
    val deviceId: String,
    val deviceVersion: Long,
    val nativeSessionId: String,
    val nativeSessionVersion: Long,
    val credentialVersion: Long,
    val admittedAt: Instant,
    val expiresAt: Instant,
) {
    constructor(
        value: CollaborationAdmission
    ) : this(
        value.companyId.toString(),
        value.accountId.toString(),
        value.deviceId.toString(),
        value.deviceVersion,
        value.nativeSessionId.toString(),
        value.nativeSessionVersion,
        value.credentialVersion,
        value.admittedAt,
        value.expiresAt,
    )
}
