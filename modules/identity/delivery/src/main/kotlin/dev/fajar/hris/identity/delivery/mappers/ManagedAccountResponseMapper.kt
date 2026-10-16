package dev.fajar.hris.identity.delivery.mappers

import dev.fajar.hris.identity.delivery.responses.ManagedAccountResponse
import dev.fajar.hris.identity.domain.entities.ManagedAccount

fun ManagedAccount.toResponse() =
    ManagedAccountResponse(
        account.id,
        account.email,
        account.displayName,
        account.active,
        invitationPending,
        account.mfaConfigured,
        platformPermissions,
        account.version,
    )
