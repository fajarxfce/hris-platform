package dev.fajar.hris.administration.domain.entities

import java.util.Collections
import java.util.UUID

/** Recorded resulting state, never a reconstruction of the account's current or previous grants. */
class PlatformAccountAccessAudit(
    val id: UUID,
    val accountId: UUID,
    val active: Boolean,
    permissions: List<String>,
    val reason: String?,
) {
    val permissions: List<String> = Collections.unmodifiableList(permissions.toList())
}
