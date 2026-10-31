package dev.fajar.hris.collaboration.domain.entities

/** Empty device lists request all currently eligible devices of an admitted account. */
data class CollaborationMatrixKeyQuery(val devices: Map<String, List<String>>) {
    override fun toString() = "CollaborationMatrixKeyQuery(<redacted>)"
}

data class CollaborationMatrixKeyQueryResult(
    val deviceKeys: Map<String, Map<String, CollaborationMatrixDeviceKeys>>
) {
    override fun toString() = "CollaborationMatrixKeyQueryResult(<redacted>)"
}
