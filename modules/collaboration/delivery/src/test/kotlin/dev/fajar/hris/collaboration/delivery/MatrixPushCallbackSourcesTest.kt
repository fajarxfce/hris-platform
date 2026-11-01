package dev.fajar.hris.collaboration.delivery

import dev.fajar.hris.collaboration.delivery.di.matrixPushCallbackSettings
import dev.fajar.hris.collaboration.delivery.matrix.MatrixPushCallbackSources
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class MatrixPushCallbackSourcesTest {
    @Test
    fun numericExactPeersAndIpv4Ipv6NetworkBoundariesAreEnforced() {
        val source =
            MatrixPushCallbackSources(listOf("127.0.0.1", "10.77.77.0/24", "fd12:3456::/48", "::1"))
        for (address in
            listOf(
                "127.0.0.1",
                "10.77.77.0",
                "10.77.77.255",
                "fd12:3456::1",
                "::1",
                "::ffff:127.0.0.1",
            )) assertTrue(source.allows(address), address)
        for (address in
            listOf(
                "127.0.0.2",
                "10.77.78.1",
                "10.77.76.255",
                "fd12:3457::1",
                "fd12:3456:1::1",
                "::2",
            )) assertFalse(source.allows(address), address)
    }

    @Test
    fun HostnamesScopedAmbiguousAndForwardedValuesNeverAuthorize() {
        val source = MatrixPushCallbackSources(listOf("127.0.0.1", "::1"))
        for (value in
            listOf(
                "localhost",
                "127.1",
                "2130706433",
                "0177.0.0.1",
                "127.0.0.01",
                "::1%lo",
                "[::1]",
                "127.0.0.1:443",
                "127.0.0.1, 10.1.2.3",
                "for=127.0.0.1",
                " ::1",
                ":::",
            )) {
            assertFalse(source.allows(value), value)
            assertThrows(IllegalArgumentException::class.java) {
                MatrixPushCallbackSources(listOf(value))
            }
        }
    }

    @Test
    fun configuredSourceCountAndPrefixesAreBounded() {
        for (entries in
            listOf(
                emptyList(),
                List(33) { "127.0.0.1" },
                listOf("127.0.0.1/0"),
                listOf("127.0.0.1/33"),
                listOf("::1/129"),
                listOf("127.0.0.1/8/16"),
                listOf("127.0.0.1/no"),
            )) assertThrows(IllegalArgumentException::class.java) {
            MatrixPushCallbackSources(entries)
        }
    }

    @Test
    fun privateCallbackOptInRequiresItsDependenciesAndUnmodifiedSocketPeers() {
        fun environment(values: Map<String, Any>) =
            StandardEnvironment().apply {
                propertySources.addFirst(MapPropertySource("fixture", values))
            }
        val valid =
            mapOf<String, Any>(
                "HRIS_COLLABORATION_PUSH_CALLBACK_ENABLED" to "true",
                "HRIS_COLLABORATION_ENABLED" to "true",
                "HRIS_MATRIX_ENABLED" to "true",
                "HRIS_COLLABORATION_PUSH_ENABLED" to "true",
                "HRIS_MATRIX_PUSH_CALLBACK_SOURCES" to "127.0.0.1, ::1",
            )
        assertTrue(matrixPushCallbackSettings(environment(valid)).enabled)
        for (key in
            listOf(
                "HRIS_COLLABORATION_ENABLED",
                "HRIS_MATRIX_ENABLED",
                "HRIS_COLLABORATION_PUSH_ENABLED",
                "HRIS_MATRIX_PUSH_CALLBACK_SOURCES",
            )) assertThrows(RuntimeException::class.java) {
            matrixPushCallbackSettings(environment(valid - key))
        }
        for (strategy in listOf("NATIVE", "FRAMEWORK")) assertThrows(
            IllegalArgumentException::class.java
        ) {
            matrixPushCallbackSettings(
                environment(valid + ("server.forward-headers-strategy" to strategy))
            )
        }
        assertFalse(
            matrixPushCallbackSettings(
                    environment(
                        mapOf("HRIS_MATRIX_PUSH_CALLBACK_SOURCES" to "unused invalid config")
                    )
                )
                .enabled
        )
    }
}
