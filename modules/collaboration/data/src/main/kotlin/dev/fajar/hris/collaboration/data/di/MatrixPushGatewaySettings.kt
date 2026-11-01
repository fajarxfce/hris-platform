package dev.fajar.hris.collaboration.data.di

import dev.fajar.hris.collaboration.data.matrix.MatrixPushGatewayConfiguration
import java.net.URI
import org.springframework.core.env.Environment

fun matrixPushGatewaySettings(environment: Environment): MatrixPushGatewayConfiguration {
    val value =
        environment.getProperty("HRIS_MATRIX_PUSH_GATEWAY_URL")
            ?: error("Missing Matrix push gateway endpoint")
    val endpoint =
        try {
            URI(value)
        } catch (_: java.net.URISyntaxException) {
            error("Invalid Matrix push gateway endpoint")
        }
    return MatrixPushGatewayConfiguration(endpoint)
}
