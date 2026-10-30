package dev.fajar.hris.communications.domain.entities

/** Accepted means SMTP acceptance, not mailbox delivery or an Inbox read. */
data class AnnouncementEmailDeliveryCounts(
    val pending: Int,
    val processing: Int,
    val accepted: Int,
    val failed: Int,
    val superseded: Int,
) {
    val trackedRecipients: Int
        get() = pending + processing + accepted + failed + superseded
}
