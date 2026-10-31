package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.di.matrixProviderSettings
import dev.fajar.hris.collaboration.data.matrix.MatrixHttpClientOwner
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class MatrixProviderSettingsTest {
    @TempDir lateinit var directory: Path

    private fun environment(secret: Path, origin: String = "http://127.0.0.1:8008") =
        StandardEnvironment().apply {
            propertySources.addFirst(
                MapPropertySource(
                    "owned-fixture",
                    mapOf(
                        "HRIS_MATRIX_ORIGIN" to origin,
                        "HRIS_MATRIX_SERVER_NAME" to "matrix.example.test",
                        "HRIS_MATRIX_JWT_ISSUER" to "urn:fixture",
                        "HRIS_MATRIX_JWT_AUDIENCE" to "matrix:fixture",
                        "HRIS_MATRIX_JWT_SECRET_FILE" to secret.toString(),
                    ),
                )
            )
        }

    @Test
    fun mountedSecretAcceptsARealTrailingNewlineAndNeverRendersItsValue() {
        val secret = "fictional-matrix-signing-secret-only-12345"
        for (suffix in listOf("", "\n", "\r\n")) {
            val file = Files.writeString(directory.resolve("signing"), secret + suffix)
            val result = matrixProviderSettings(environment(file))
            assertEquals(secret, result.jwtSecret)
            assertFalse(result.toString().contains(secret))
        }
    }

    @Test
    fun missingOversizedNonAsciiAndMultilineSecretsFailWithoutIncludingTheirContent() {
        assertThrows(IllegalStateException::class.java) {
            matrixProviderSettings(environment(directory.resolve("missing")))
        }
        assertThrows(IllegalStateException::class.java) {
            matrixProviderSettings(environment(directory))
        }
        for (bad in
            listOf(
                "private-short",
                "private-" + "x".repeat(100000),
                "private-" + "é".repeat(32),
                "private-" + "x".repeat(32) + "\nline",
            )) {
            val file = Files.writeString(directory.resolve("invalid"), bad)
            val error =
                assertThrows(RuntimeException::class.java) {
                    matrixProviderSettings(environment(file))
                }
            assertFalse(error.message.orEmpty().contains("private-"))
        }
        val file = Files.writeString(directory.resolve("valid"), "x".repeat(32))
        assertThrows(IllegalArgumentException::class.java) {
            matrixProviderSettings(environment(file, "http://user:pass@example.test/path"))
        }
    }

    @Test
    fun enabledProviderRequiresAUsableIdentityEncryptionKeyBeforeReadingItsSettings() {
        assertThrows(IllegalStateException::class.java) {
            dev.fajar.hris.collaboration.data.di
                .CollaborationConfiguration()
                .collaborationMatrixConfiguration(
                    StandardEnvironment(),
                    dev.fajar.hris.identity.data.crypto.IdentityKeyring("empty", emptyMap()),
                )
        }
    }

    @Test
    fun applicationOwnerClosesItsClientAndRepeatedDisposalDoesNotRestartIt() {
        val owner = MatrixHttpClientOwner()
        assertFalse(owner.client.isTerminated)
        owner.close()
        assertTrue(owner.client.isTerminated)
        owner.close()
        assertTrue(owner.client.isTerminated)
    }
}
