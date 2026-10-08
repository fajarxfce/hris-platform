package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.data.datasources.AccountAdministrationRow
import dev.fajar.hris.identity.domain.entities.Account
import dev.fajar.hris.identity.domain.entities.ManagedAccount

fun AccountAdministrationRow.toManagedAccount(permissions: Set<String>) =
    ManagedAccount(
        Account(id, email, displayName, active, mfaConfigured, version, securityVersion),
        invitationPending,
        permissions,
    )
