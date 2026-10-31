package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant

interface MatrixJwtDataSource {
    fun sign(subject: String, issuedAt: Instant, expiresAt: Instant): String
}

interface MatrixClientDataSource {
    fun login(jwt: String, deviceId: String, deadline: Instant): MatrixLoginData

    fun whoAmI(accessToken: String, deadline: Instant): MatrixIdentityData

    fun logoutDevice(accessToken: String)
}

data class MatrixIdentityData(val userId: String, val deviceId: String, val isGuest: Boolean)

class MatrixLoginData(
    val userId: String,
    val deviceId: String,
    val accessToken: String,
    val expiresAt: Instant,
) {
    override fun toString() = "MatrixLoginData(<redacted>)"
}
