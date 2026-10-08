package dev.fajar.hris.identity.delivery.oidc

import dev.fajar.hris.identity.delivery.security.SessionIdentity
import org.springframework.security.oauth2.core.oidc.user.OidcUser

/** Ephemeral during the callback; the success handler persists only SessionIdentity. */
class ApplicationOidcUser(val identity: SessionIdentity, private val delegate: OidcUser) :
    OidcUser by delegate
