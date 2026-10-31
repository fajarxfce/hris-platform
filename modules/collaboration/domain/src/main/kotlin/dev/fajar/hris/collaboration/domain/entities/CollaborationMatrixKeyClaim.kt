package dev.fajar.hris.collaboration.domain.entities

/**
 * Public explicit device/algorithm selection. One claim can consume one provider key per device.
 */
data class CollaborationMatrixKeyClaim(val oneTimeKeys: Map<String, Map<String, String>>) {
    override fun toString() = "CollaborationMatrixKeyClaim(<redacted>)"
}

data class CollaborationMatrixKeyClaimResult(
    val oneTimeKeys: Map<String, Map<String, Map<String, CollaborationMatrixSignedKey>>>
) {
    override fun toString() = "CollaborationMatrixKeyClaimResult(<redacted>)"
}
