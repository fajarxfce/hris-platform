package dev.fajar.hris.documents.data.datasources

import java.net.InetSocketAddress
import java.time.Duration

data class ClamAvSettings(
    val endpoint: InetSocketAddress,
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val writeTimeout: Duration = Duration.ofSeconds(10),
    val responseTimeout: Duration = Duration.ofSeconds(70),
    val totalTimeout: Duration = Duration.ofSeconds(120),
    val concurrency: Int = 2,
) {
    init {
        require(!endpoint.isUnresolved && endpoint.port in 1..65535)
        require(
            connectTimeout.toMillis() in 100..5000 &&
                writeTimeout.toMillis() in 100..10000 &&
                responseTimeout.toMillis() in 100..90000 &&
                totalTimeout.toMillis() in 100..180000 &&
                concurrency in 1..4
        )
    }
}
