package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationReadinessControl
import java.util.UUID

const val COLLABORATION_READINESS_CONTROL_TYPE = "dev.fajar.hris.recipient_ready"
private const val LIFETIME_MILLIS = 86_400_000L
private const val MAX_PUBLICATION_MILLIS = 253_402_214_399_999L
private val CONTROL_FIELDS =
    setOf(
        "schema_version",
        "event_id",
        "company_id",
        "account_id",
        "native_session_id",
        "device_id",
        "published_at",
        "expires_at",
        "publication_revision",
    )

/** Fixed v2 wire shape; revisions follow publication commit order, not event timestamps. */
fun collaborationReadinessControlContent(
    control: CollaborationReadinessControl
): Map<String, String> {
    require(control.publishedAtEpochMillis in 1..MAX_PUBLICATION_MILLIS)
    require(control.publicationRevision > 0)
    return mapOf(
        "schema_version" to "2",
        "event_id" to control.eventId.toString(),
        "company_id" to control.companyId.toString(),
        "account_id" to control.accountId.toString(),
        "native_session_id" to control.nativeSessionId.toString(),
        "device_id" to control.deviceId.toString(),
        "published_at" to control.publishedAtEpochMillis.toString(),
        "expires_at" to (control.publishedAtEpochMillis + LIFETIME_MILLIS).toString(),
        "publication_revision" to control.publicationRevision.toString(),
    )
}

/** Shape validation only; callers must separately admit the current device/session and time. */
fun readCollaborationReadinessControl(
    content: Map<String, String>
): CollaborationReadinessControl? {
    if (content.keys != CONTROL_FIELDS || content["schema_version"] != "2") return null
    val revision = content["publication_revision"]?.toLongOrNull() ?: return null
    if (revision <= 0 || content["publication_revision"] != revision.toString()) return null
    val published = content["published_at"]?.toLongOrNull() ?: return null
    if (
        published !in 1..MAX_PUBLICATION_MILLIS ||
            content["published_at"] != published.toString() ||
            content["expires_at"] != (published + LIFETIME_MILLIS).toString()
    )
        return null
    fun id(name: String): UUID? {
        val value = content.getValue(name)
        if (value.length != 36) return null
        return try {
            UUID.fromString(value).takeIf { it.toString() == value }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    return CollaborationReadinessControl(
        id("event_id") ?: return null,
        id("company_id") ?: return null,
        id("account_id") ?: return null,
        id("native_session_id") ?: return null,
        id("device_id") ?: return null,
        published,
        revision,
    )
}
