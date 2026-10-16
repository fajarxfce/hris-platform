package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.Instant

/** Revalidates platform authority and session assurance under the actor account guard. */
fun validatePlatformSessionActor(
    actor: Actor,
    access: AccountAccess?,
    at: Instant,
    security: IdentitySecurityPolicy,
): Result<Actor> =
    validatePlatformCommandActor(actor, access).flatMap { current ->
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
