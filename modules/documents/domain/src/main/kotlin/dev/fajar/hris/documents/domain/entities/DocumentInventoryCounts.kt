package dev.fajar.hris.documents.domain.entities

data class DocumentInventoryCounts(
    val retained: Int = 0,
    val unknown: Int = 0,
    val anomalous: Int = 0,
    val queued: Int = 0,
    val recoveryExhausted: Int = 0,
    val scheduled: Int = 0,
) {
    val scanned: Int
        get() = retained + unknown + anomalous + queued + recoveryExhausted + scheduled

    operator fun plus(other: DocumentInventoryCounts) =
        DocumentInventoryCounts(
            retained + other.retained,
            unknown + other.unknown,
            anomalous + other.anomalous,
            queued + other.queued,
            recoveryExhausted + other.recoveryExhausted,
            scheduled + other.scheduled,
        )
}
