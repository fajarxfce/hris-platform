package dev.fajar.hris.identity.delivery.requests

import dev.fajar.hris.identity.domain.entities.PushPlatform

data class SaveNativePushRegistrationRequest(
    val expectedVersion: Long?,
    val platform: PushPlatform,
    val token: String,
) {
    override fun toString(): String = "SaveNativePushRegistrationRequest(<redacted>)"
}
