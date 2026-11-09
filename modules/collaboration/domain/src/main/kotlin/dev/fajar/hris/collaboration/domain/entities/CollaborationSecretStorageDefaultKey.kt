package dev.fajar.hris.collaboration.domain.entities

/** Public key identifier only; null represents Matrix's explicit empty/disabled event. */
data class CollaborationSecretStorageDefaultKey(val keyId: String?) {
    override fun toString() = "CollaborationSecretStorageDefaultKey(<redacted>)"
}
