package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.identity.domain.policies.validNativeToken

const val COLLABORATION_SPACE_GATEWAY_ACCESS_PREFIX = "hris_mxg_a_"
const val COLLABORATION_SPACE_GATEWAY_REFRESH_PREFIX = "hris_mxg_r_"

fun validCollaborationSpaceGatewayAccess(token: String): Boolean =
    token.startsWith(COLLABORATION_SPACE_GATEWAY_ACCESS_PREFIX) &&
        validNativeToken(token.removePrefix(COLLABORATION_SPACE_GATEWAY_ACCESS_PREFIX))

fun validCollaborationSpaceGatewayRefresh(token: String): Boolean =
    token.startsWith(COLLABORATION_SPACE_GATEWAY_REFRESH_PREFIX) &&
        validNativeToken(token.removePrefix(COLLABORATION_SPACE_GATEWAY_REFRESH_PREFIX))
