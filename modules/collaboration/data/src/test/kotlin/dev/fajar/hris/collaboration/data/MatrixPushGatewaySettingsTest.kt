package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.di.matrixPushGatewaySettings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class MatrixPushGatewaySettingsTest {
    private fun environment(value: String? = null) =
        StandardEnvironment().apply {
            propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
            propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
            value?.let {
                propertySources.addFirst(
                    MapPropertySource("fixture", mapOf("HRIS_MATRIX_PUSH_GATEWAY_URL" to it))
                )
            }
        }

    @Test
    fun onlyAnExplicitFixedHttpsCallbackCanEnableTheGateway() {
        assertEquals(
            "Missing Matrix push gateway endpoint",
            assertThrows(IllegalStateException::class.java) {
                    matrixPushGatewaySettings(environment())
                }
                .message,
        )
        for (value in
            listOf(
                "private invalid URI",
                "",
                "http://push.example.test/_matrix/push/v1/notify",
                "https://private:secret@push.example.test/_matrix/push/v1/notify",
                "https://push.example.test/_matrix/push/v1/notify?private=secret",
            )) {
            val error =
                assertThrows(RuntimeException::class.java) {
                    matrixPushGatewaySettings(environment(value))
                }
            assertEquals("Invalid Matrix push gateway endpoint", error.message)
            assertNull(error.cause)
        }
        val value = "https://push.example.test/_matrix/push/v1/notify"
        val configured = matrixPushGatewaySettings(environment(value))
        assertEquals(value, configured.endpoint.toASCIIString())
        assertEquals("MatrixPushGatewayConfiguration(<redacted>)", configured.toString())
    }
}
