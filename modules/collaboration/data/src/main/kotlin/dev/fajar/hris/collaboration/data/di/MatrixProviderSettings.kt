package dev.fajar.hris.collaboration.data.di

import dev.fajar.hris.collaboration.data.matrix.MatrixProviderConfiguration
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.core.env.Environment

/** Read only when both service and provider are enabled. Never render secret bytes in errors. */
fun matrixProviderSettings(environment: Environment): MatrixProviderConfiguration {
    fun required(name: String): String =
        environment.getProperty(name)?.takeIf { it.isNotBlank() }
            ?: error("Missing Matrix provider setting: $name")
    val origin =
        try {
            URI(required("HRIS_MATRIX_ORIGIN"))
        } catch (_: IllegalArgumentException) {
            error("Invalid internal Matrix origin")
        } catch (_: java.net.URISyntaxException) {
            error("Invalid internal Matrix origin")
        }
    val secretPath = Path.of(required("HRIS_MATRIX_JWT_SECRET_FILE"))
    val bytes =
        try {
            check(Files.isRegularFile(secretPath)) {
                "Matrix signing secret must be a regular file"
            }
            Files.newInputStream(secretPath).use { it.readNBytes(515) }
        } catch (_: java.io.IOException) {
            error("Cannot read Matrix signing secret file")
        }
    try {
        check(bytes.size <= 514) { "Invalid Matrix signing secret length" }
        val secret = String(bytes, Charsets.US_ASCII).removeSuffix("\n").removeSuffix("\r")
        check(bytes.all { it.toInt() in 0..127 }) { "Invalid Matrix signing secret encoding" }
        return MatrixProviderConfiguration(
            origin,
            required("HRIS_MATRIX_SERVER_NAME"),
            required("HRIS_MATRIX_JWT_ISSUER"),
            required("HRIS_MATRIX_JWT_AUDIENCE"),
            secret,
        )
    } finally {
        bytes.fill(0)
    }
}
