package dev.fajar.hris.administration.data

import dev.fajar.hris.administration.data.di.AdministrationConfiguration
import dev.fajar.hris.administration.data.repositories.ConfiguredIntegrationRepository
import dev.fajar.hris.administration.domain.entities.IntegrationKind
import dev.fajar.hris.core.domain.Result
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.env.PropertySource
import org.springframework.core.env.StandardEnvironment

class IntegrationConfigurationTest {
    @Test
    fun startupFlagsAreDetachedAndPublishedAsAnImmutableCompleteSet() {
        val enabled = mutableSetOf(IntegrationKind.STORAGE, IntegrationKind.MAIL)
        val repository = ConfiguredIntegrationRepository(enabled)
        enabled.clear()
        val snapshot = repository.read().value
        assertEquals(IntegrationKind.entries, snapshot.map { it.kind })
        assertEquals(
            setOf(IntegrationKind.STORAGE, IntegrationKind.MAIL),
            snapshot.filter { it.enabled }.map { it.kind }.toSet(),
        )
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot as MutableList).clear()
        }
        assertEquals(snapshot, repository.read().value)
    }

    @Test
    fun configurationBindingNeverAcquiresProviderLocationsOrCredentials() {
        val flags =
            mapOf(
                "HRIS_FIREBASE_AUTH_ENABLED" to "true",
                "HRIS_OIDC_ENABLED" to "false",
                "HRIS_MAIL_ENABLED" to "false",
                "HRIS_FCM_ENABLED" to "true",
                "HRIS_STORAGE_ENABLED" to "false",
                "HRIS_DOCUMENT_SCANNER_ENABLED" to "true",
            )
        val requested = mutableListOf<String>()
        val environment =
            StandardEnvironment().apply {
                propertySources.addFirst(
                    object : PropertySource<Map<String, String>>("owned-flags", flags) {
                        override fun getProperty(name: String): Any? {
                            assertTrue(
                                name in flags,
                                "Only explicit enablement flags may be acquired",
                            )
                            requested += name
                            return source[name]
                        }
                    }
                )
            }
        val configuration = AdministrationConfiguration().integrationConfiguration(environment)
        val snapshot = (configuration.read() as Result.Success).value
        assertEquals(flags.keys, requested.toSet())
        assertEquals(
            setOf(
                IntegrationKind.FIREBASE_AUTH,
                IntegrationKind.PUSH,
                IntegrationKind.DOCUMENT_SCANNER,
            ),
            snapshot.filter { it.enabled }.map { it.kind }.toSet(),
        )
        requested.clear()
        configuration.read()
        assertTrue(requested.isEmpty())
    }
}
