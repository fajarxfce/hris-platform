package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.identity.domain.policies.validNativeToken

const val COLLABORATION_GATEWAY_ACCESS_PREFIX = "hris_mx_a_"
const val COLLABORATION_GATEWAY_REFRESH_PREFIX = "hris_mx_r_"

fun validCollaborationGatewayAccess(token: String): Boolean =
    token.startsWith(COLLABORATION_GATEWAY_ACCESS_PREFIX) &&
        validNativeToken(token.removePrefix(COLLABORATION_GATEWAY_ACCESS_PREFIX))

fun validCollaborationGatewayRefresh(token: String): Boolean =
    token.startsWith(COLLABORATION_GATEWAY_REFRESH_PREFIX) &&
        validNativeToken(token.removePrefix(COLLABORATION_GATEWAY_REFRESH_PREFIX))
