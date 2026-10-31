package dev.fajar.hris.collaboration.data.matrix

import java.net.URI
import java.time.Duration

/** Operator-owned internal origin. No user, event or provider response can select its URL. */
class MatrixProviderConfiguration(
    val origin: URI,
    val serverName: String,
    val issuer: String,
    val audience: String,
    val jwtSecret: String,
    val requestTimeout: Duration = Duration.ofSeconds(5),
) {
    init {
        require(
            origin.scheme in setOf("http", "https") &&
                origin.host != null &&
                origin.rawUserInfo == null &&
                origin.rawQuery == null &&
                origin.rawFragment == null &&
                origin.rawPath in setOf("", "/") &&
                origin.port in -1..65535 &&
                origin.port != 0
        ) {
            "Invalid internal Matrix origin"
        }
        val server = runCatching { URI("https://$serverName") }.getOrNull()
        require(
            serverName.length in 1..180 &&
                server?.host != null &&
                server.rawAuthority == serverName &&
                server.rawUserInfo == null &&
                server.rawPath == "" &&
                server.rawQuery == null &&
                server.rawFragment == null &&
                server.port in -1..65535 &&
                server.port != 0
        ) {
            "Invalid Matrix server name"
        }
        require(
            listOf(issuer, audience).all { value ->
                value.length in 1..128 && value.all { it.code in 33..126 }
            }
        ) {
            "Invalid Matrix JWT scope"
        }
        require(jwtSecret.length in 32..512 && jwtSecret.all { it.code in 33..126 }) {
            "Invalid Matrix JWT secret"
        }
        require(requestTimeout >= Duration.ofMillis(1) && requestTimeout <= Duration.ofSeconds(5))
    }

    override fun toString() = "MatrixProviderConfiguration(<redacted>)"
}
