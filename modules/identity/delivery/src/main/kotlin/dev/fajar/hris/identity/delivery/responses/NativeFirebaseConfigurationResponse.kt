package dev.fajar.hris.identity.delivery.responses

data class NativeFirebaseConfigurationResponse(
    val enabled: Boolean,
    val projectId: String?,
    val providers: List<String>,
)
