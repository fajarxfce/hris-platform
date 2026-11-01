package dev.fajar.hris.collaboration.data.matrix

import java.net.URI

/** Operator-owned endpoint; neither clients nor provider responses choose a callback URL. */
class MatrixPushGatewayConfiguration(val endpoint: URI) {
    init {
        require(
            endpoint.toASCIIString().length <= 512 &&
                endpoint.scheme == "https" &&
                endpoint.host != null &&
                endpoint.rawUserInfo == null &&
                endpoint.rawQuery == null &&
                endpoint.rawFragment == null &&
                endpoint.rawPath == "/_matrix/push/v1/notify" &&
                endpoint.port in -1..65535 &&
                endpoint.port != 0
        ) {
            "Invalid Matrix push gateway endpoint"
        }
    }

    override fun toString() = "MatrixPushGatewayConfiguration(<redacted>)"
}
