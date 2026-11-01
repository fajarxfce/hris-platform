package dev.fajar.hris.collaboration.delivery.di

import dev.fajar.hris.collaboration.delivery.matrix.MatrixPushCallbackSources
import org.springframework.core.env.Environment

class MatrixPushCallbackSettings(val sources: MatrixPushCallbackSources?) {
    val enabled: Boolean
        get() = sources != null
}

fun matrixPushCallbackSettings(environment: Environment): MatrixPushCallbackSettings {
    if (
        !environment.getProperty(
            "HRIS_COLLABORATION_PUSH_CALLBACK_ENABLED",
            Boolean::class.java,
            false,
        )
    )
        return MatrixPushCallbackSettings(null)
    require(
        listOf(
                "HRIS_COLLABORATION_ENABLED",
                "HRIS_MATRIX_ENABLED",
                "HRIS_COLLABORATION_PUSH_ENABLED",
            )
            .all { environment.getProperty(it, Boolean::class.java, false) }
    ) {
        "Matrix push callback requires collaboration, Matrix and push"
    }
    require(
        environment
            .getProperty("server.forward-headers-strategy", "NONE")
            .equals("NONE", ignoreCase = true)
    ) {
        "Private Matrix callback must use socket peer addresses"
    }
    val configured =
        environment.getProperty("HRIS_MATRIX_PUSH_CALLBACK_SOURCES")
            ?: error("Missing Matrix callback source networks")
    require(configured.length <= 2048) { "Too many Matrix callback source networks" }
    return MatrixPushCallbackSettings(
        MatrixPushCallbackSources(configured.split(',').map { it.trim() })
    )
}
