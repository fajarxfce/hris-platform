package dev.fajar.hris.identity.delivery.oidc

import java.net.URI

data class OidcClientSettings(
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
    val publicUrl: String,
    val authorizationUri: String,
    val tokenUri: String,
    val jwkSetUri: String,
    val authenticationMethod: String,
    val allowLoopbackHttp: Boolean = false,
) {
    init {
        require(
            clientId.isNotBlank() &&
                clientId.length <= 255 &&
                clientSecret.isNotBlank() &&
                clientSecret.length <= 4096
        ) {
            "OIDC client credentials are required"
        }
        require(authenticationMethod in setOf("client_secret_basic", "client_secret_post")) {
            "Unsupported OIDC client authentication method"
        }
        for (value in listOf(issuer, publicUrl, authorizationUri, tokenUri, jwkSetUri)) {
            require(value.length in 1..1000) { "OIDC endpoint is required" }
            val uri = URI(value)
            require(
                uri.host != null &&
                    uri.userInfo == null &&
                    uri.fragment == null &&
                    (uri.scheme == "https" ||
                        (allowLoopbackHttp &&
                            uri.scheme == "http" &&
                            uri.host in setOf("127.0.0.1", "localhost", "[::1]")))
            ) {
                "OIDC endpoints must use HTTPS"
            }
        }
        val origin = URI(publicUrl)
        require(origin.query == null && origin.path.isNullOrEmpty()) {
            "OIDC public URL must be an origin without a path"
        }
    }

    override fun toString(): String = "OidcClientSettings(configured)"
}
