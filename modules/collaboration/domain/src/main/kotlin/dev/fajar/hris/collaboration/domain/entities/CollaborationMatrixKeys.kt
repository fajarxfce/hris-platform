package dev.fajar.hris.collaboration.domain.entities

/** Public Matrix keys only. Maps are detached and bounded by the admission policy. */
data class CollaborationMatrixDeviceKeys(
    val userId: String,
    val deviceId: String,
    val algorithms: List<String>,
    val keys: Map<String, String>,
    val signatures: Map<String, Map<String, String>>,
) {
    override fun toString() = "CollaborationMatrixDeviceKeys(<redacted>)"
}

data class CollaborationMatrixSignedKey(
    val key: String,
    val signatures: Map<String, Map<String, String>>,
    val fallback: Boolean? = null,
) {
    override fun toString() = "CollaborationMatrixSignedKey(<redacted>)"
}

data class CollaborationMatrixKeyUpload(
    val deviceKeys: CollaborationMatrixDeviceKeys? = null,
    val oneTimeKeys: Map<String, CollaborationMatrixSignedKey> = emptyMap(),
    val fallbackKeys: Map<String, CollaborationMatrixSignedKey> = emptyMap(),
) {
    val hasSignedKeys: Boolean
        get() = oneTimeKeys.isNotEmpty() || fallbackKeys.isNotEmpty()

    override fun toString() = "CollaborationMatrixKeyUpload(<redacted>)"
}

data class CollaborationMatrixKeyCounts(val oneTimeKeyCounts: Map<String, Int>)
