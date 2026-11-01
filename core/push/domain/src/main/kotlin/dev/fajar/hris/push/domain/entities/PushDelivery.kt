package dev.fajar.hris.push.domain.entities

/**
 * Delivery intent. Notification fallback copy must remain generic, without private message text.
 */
sealed interface PushDelivery {
    val collapseKey: String?

    data class Background(override val collapseKey: String? = null) : PushDelivery {
        override fun toString() = "Background(<redacted>)"
    }

    /**
     * Android posts from the admitted local client; Apple can show this fallback during recovery.
     */
    data class Visible(
        val fallbackTitle: String,
        val fallbackBody: String,
        override val collapseKey: String? = null,
    ) : PushDelivery {
        override fun toString() = "Visible(<redacted>)"
    }
}
