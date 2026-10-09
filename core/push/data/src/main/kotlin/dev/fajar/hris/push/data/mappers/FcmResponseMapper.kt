package dev.fajar.hris.push.data.mappers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.data.datasources.PushHttpResponse
import dev.fajar.hris.push.domain.entities.PushOutcome
import tools.jackson.databind.ObjectMapper

fun fcmOutcome(response: PushHttpResponse, json: ObjectMapper): Result<PushOutcome> {
    if (response.status == 200) {
        val name = json.readTree(response.body).get("name")?.asString()
        return if (
            name != null &&
                name.length <= 512 &&
                name.startsWith("projects/") &&
                name.contains("/messages/")
        )
            Result.Success(PushOutcome.ACCEPTED)
        else Result.Failed(Failure(FailureKind.UNEXPECTED, "push_response_invalid"))
    }
    if (response.status == 429) {
        val seconds = response.retryAfter?.toLongOrNull()?.takeIf { it in 1..3600 }?.toString()
        return Result.Failed(
            Failure(
                FailureKind.RATE_LIMITED,
                "push_rate_limited",
                parameters = seconds?.let { mapOf("retryAfterSeconds" to it) } ?: emptyMap(),
            )
        )
    }
    if (response.status in 500..599 || response.status == 408)
        return Result.Failed(Failure(FailureKind.UNAVAILABLE, "push_delivery_unavailable"))
    if (response.status == 401)
        return Result.Failed(Failure(FailureKind.UNEXPECTED, "push_credentials_rejected"))
    if (response.status in setOf(400, 403, 404)) {
        val details = json.readTree(response.body).get("error")?.get("details")
        val codes =
            if (details != null && details.isArray)
                details
                    .iterator()
                    .asSequence()
                    .take(16)
                    .filter {
                        it.get("@type")?.asString() ==
                            "type.googleapis.com/google.firebase.fcm.v1.FcmError"
                    }
                    .mapNotNull { it.get("errorCode")?.asString() }
                    .toSet()
            else emptySet()
        if (response.status == 404 && "UNREGISTERED" in codes)
            return Result.Success(PushOutcome.UNREGISTERED)
        if ("SENDER_ID_MISMATCH" in codes)
            return Result.Failed(Failure(FailureKind.UNEXPECTED, "push_sender_mismatch"))
        if (response.status == 403)
            return Result.Failed(Failure(FailureKind.UNEXPECTED, "push_credentials_rejected"))
    }
    return Result.Failed(Failure(FailureKind.UNEXPECTED, "push_delivery_rejected"))
}
