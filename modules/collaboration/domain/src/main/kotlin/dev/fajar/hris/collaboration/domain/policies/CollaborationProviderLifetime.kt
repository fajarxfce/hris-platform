package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationProviderSession
import java.time.Instant

/** Credential freshness only, never a substitute for live HRIS admission or owned local history. */
fun collaborationProviderCredentialIsCurrent(
    session: CollaborationProviderSession,
    at: Instant,
): Boolean {
    val expiresAt = session.expiresAt ?: return false
    return session.createdAt <= session.updatedAt &&
        session.updatedAt <= at &&
        at < expiresAt &&
        expiresAt <= session.updatedAt.plusSeconds(600)
}
