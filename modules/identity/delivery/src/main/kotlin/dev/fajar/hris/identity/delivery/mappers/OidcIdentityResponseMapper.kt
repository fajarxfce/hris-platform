package dev.fajar.hris.identity.delivery.mappers

import dev.fajar.hris.identity.delivery.responses.OidcIdentityResponse
import dev.fajar.hris.identity.domain.entities.OidcIdentity

fun OidcIdentity.toResponse() = OidcIdentityResponse(id, issuer, subject, active, version, linkedAt)
