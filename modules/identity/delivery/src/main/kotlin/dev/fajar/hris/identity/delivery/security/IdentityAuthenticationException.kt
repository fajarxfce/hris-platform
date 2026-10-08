package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.Failure
import org.springframework.security.core.AuthenticationException

class IdentityAuthenticationException(val failure: Failure) : AuthenticationException(failure.code)
