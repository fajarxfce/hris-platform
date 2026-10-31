package dev.fajar.hris.collaboration.domain.policies

import java.util.UUID

fun collaborationMatrixLocalpart(companyId: UUID, accountId: UUID): String =
    "hris_${companyId.toString().replace("-", "")}_${accountId.toString().replace("-", "")}"

fun collaborationMatrixDeviceId(deviceId: UUID): String =
    "HRIS_${deviceId.toString().replace("-", "")}"
