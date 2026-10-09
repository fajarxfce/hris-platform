package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.Instant

/**
 * Revalidates an interactive company's authority and assurance after its resource/access guards.
 */
fun validateCompanySessionActor(
    actor: Actor,
    access: AccountAccess?,
    at: Instant,
    security: IdentitySecurityPolicy,
): Result<Actor> =
    validateCompanyCommandActor(actor, access).flatMap { current ->
        val live = requireNotNull(access)
        validateSessionAssurance(
                live.account,
                live.securityPermissions,
                actor.mfaVerifiedAt,
                at,
                security,
            )
            .map { current }
    }
