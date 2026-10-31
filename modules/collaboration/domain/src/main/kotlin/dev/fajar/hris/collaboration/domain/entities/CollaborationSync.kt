package dev.fajar.hris.collaboration.domain.entities

data class CollaborationSyncRequest(
    val since: String?,
    val timeoutMillis: Int,
    val fullState: Boolean = false,
)

data class CollaborationMatrixSyncBatch(
    val providerSince: String,
    val providerFilterId: String,
    val payload: Map<String, Any>,
) {
    override fun toString() = "CollaborationMatrixSyncBatch(<redacted>)"
}

data class CollaborationSyncResult(val nextBatch: String, val payload: Map<String, Any>) {
    override fun toString() = "CollaborationSyncResult(<redacted>)"
}
