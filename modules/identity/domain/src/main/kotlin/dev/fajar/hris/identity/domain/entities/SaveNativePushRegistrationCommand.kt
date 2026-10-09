package dev.fajar.hris.identity.domain.entities

data class SaveNativePushRegistrationCommand(
    val expectedVersion: Long?,
    val platform: PushPlatform,
    val token: String,
) {
    override fun toString(): String = "SaveNativePushRegistrationCommand(<redacted>)"
}
