package dev.fajar.hris.collaboration.domain.entities

/** Only unknown or permanently retired capabilities may be removed by the provider. */
class CollaborationPushReceipt(rejected: List<CollaborationPushKey>) {
    val rejected: List<CollaborationPushKey> = rejected.toList()

    override fun toString() = "CollaborationPushReceipt(<redacted>)"
}
