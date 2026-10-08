package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.requests.*
import dev.fajar.hris.identity.domain.entities.CredentialChallengeKind
import dev.fajar.hris.identity.domain.usecases.*
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AccountCredentialController(
    private val recover: RequestPasswordRecovery,
    private val confirm: ConfirmAccountCredential,
) {
    @PostMapping("/password-recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun recover(@RequestBody body: PasswordRecoveryRequest, request: HttpServletRequest) {
        recover
            .execute(
                body.email,
                request.remoteAddr,
                request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID(),
            )
            .response()
    }

    @PostMapping("/invitations/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun accept(@RequestBody body: AccountCredentialRequest, request: HttpServletRequest) {
        confirm
            .execute(
                CredentialChallengeKind.INVITATION,
                body.token,
                body.password,
                request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID(),
            )
            .response()
    }

    @PostMapping("/password-recovery/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirm(@RequestBody body: AccountCredentialRequest, request: HttpServletRequest) {
        confirm
            .execute(
                CredentialChallengeKind.PASSWORD_RECOVERY,
                body.token,
                body.password,
                request.getAttribute("hris.correlationId") as? UUID ?: UUID.randomUUID(),
            )
            .response()
    }
}
