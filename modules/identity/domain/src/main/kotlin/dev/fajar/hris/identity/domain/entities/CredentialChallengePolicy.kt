package dev.fajar.hris.identity.domain.entities

class CredentialChallengePolicy(
    val enabled: Boolean = false,
    val publicOrigin: String = "",
    val invitationSeconds: Long = 259200,
    val recoverySeconds: Long = 1800,
    val attempts: AuthenticationAttemptPolicy =
        AuthenticationAttemptPolicy(perOrigin = 30, perAccount = 3),
    val maximumDeliveryAttempts: Int = 8,
    val mailLeaseSeconds: Int = 120,
) {
    init {
        require(invitationSeconds in 3600..604800 && recoverySeconds in 300..3600)
        require(maximumDeliveryAttempts in 1..8 && mailLeaseSeconds in 30..300)
    }
}
