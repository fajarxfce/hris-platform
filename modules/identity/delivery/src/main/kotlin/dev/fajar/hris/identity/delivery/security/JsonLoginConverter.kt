package dev.fajar.hris.identity.delivery.security

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.identity.delivery.requests.LoginRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationConverter
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

class JsonLoginConverter(private val json: ObjectMapper) : AuthenticationConverter {
    override fun convert(request: HttpServletRequest): Authentication {
        val bytes = request.inputStream.readNBytes(8193)
        if (bytes.size > 8192)
            throw IdentityAuthenticationException(
                Failure(FailureKind.VALIDATION, "invalid_request")
            )
        val input =
            try {
                json.readValue(bytes, LoginRequest::class.java)
            } catch (_: JacksonException) {
                throw IdentityAuthenticationException(
                    Failure(FailureKind.VALIDATION, "invalid_request")
                )
            }
        return UsernamePasswordAuthenticationToken.unauthenticated(input.email, input.password)
            .apply { details = LoginOrigin(request.remoteAddr) }
    }
}
