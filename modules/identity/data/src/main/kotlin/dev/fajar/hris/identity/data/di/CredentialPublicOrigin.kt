package dev.fajar.hris.identity.data.di

import java.net.URI

fun credentialPublicOrigin(value: String, allowLoopbackHttp: Boolean): String {
    require(value.length <= 500) { "Authentication public origin is invalid" }
    val uri = URI(value)
    val allowedLoopback =
        allowLoopbackHttp &&
            uri.scheme == "http" &&
            uri.host in setOf("127.0.0.1", "[::1]", "localhost")
    require(
        (uri.scheme == "https" || allowedLoopback) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawPath in listOf("", "/") &&
            uri.port in -1..65535
    ) {
        "Authentication public origin is invalid"
    }
    return value.removeSuffix("/")
}
