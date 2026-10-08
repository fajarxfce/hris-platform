package dev.fajar.hris.storage.data.di

import java.net.URI
import java.time.Duration

class ObjectStorageSettings(
    val endpoint: URI,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val allowPlaintext: Boolean = false,
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(8),
    val callTimeout: Duration = Duration.ofSeconds(20),
) {
    init {
        require(endpoint.scheme == "https" || (allowPlaintext && endpoint.scheme == "http"))
        require(
            endpoint.host != null &&
                endpoint.userInfo == null &&
                endpoint.query == null &&
                endpoint.fragment == null &&
                endpoint.path in listOf("", "/")
        )
        require(region.matches(Regex("[a-z0-9-]{1,80}")))
        require(bucket.matches(Regex("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")))
        require(accessKey.isNotBlank() && secretKey.isNotBlank())
        require(
            connectTimeout.toMillis() in 100..10000 &&
                readTimeout.toMillis() in 100..20000 &&
                callTimeout.toMillis() in 100..30000
        )
    }
}
