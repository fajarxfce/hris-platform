package dev.fajar.hris.identity.delivery.requests

import java.util.UUID

data class MfaConfirmationRequest(val operationId: UUID, val code: String) {
    override fun toString(): String = "MfaConfirmationRequest(<redacted>)"
}
