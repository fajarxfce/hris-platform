package dev.fajar.hris.identity.delivery.mappers

import dev.fajar.hris.identity.delivery.responses.*
import dev.fajar.hris.identity.domain.entities.CurrentAccount

fun CurrentAccount.toResponse(): SessionResponse =
    SessionResponse(
        AccountResponse(
            account.id.toString(),
            account.email,
            account.displayName,
            account.mfaConfigured,
        ),
        permissions.sorted(),
        companies.map {
            MembershipResponse(it.companyId.toString(), it.companyName, it.companyCode, it.timezone)
        },
        SessionAssuranceResponse(
            assurance.required,
            assurance.verified,
            assurance.setupAvailable,
            assurance.validUntil?.toString(),
            assurance.recentUntil?.toString(),
        ),
    )
