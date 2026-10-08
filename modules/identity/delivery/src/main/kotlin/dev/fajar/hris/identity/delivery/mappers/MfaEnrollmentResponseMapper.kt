package dev.fajar.hris.identity.delivery.mappers

import dev.fajar.hris.identity.delivery.responses.MfaEnrollmentResponse
import dev.fajar.hris.identity.domain.entities.MfaEnrollment
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun MfaEnrollment.toResponse(): MfaEnrollmentResponse {
    val issuer = "HRIS Platform"
    val label = URLEncoder.encode("$issuer:$email", StandardCharsets.UTF_8).replace("+", "%20")
    val encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
    return MfaEnrollmentResponse(
        operationId.toString(),
        secret,
        "otpauth://totp/$label?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=6&period=30",
        expiresAt.toString(),
    )
}
