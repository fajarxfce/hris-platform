package dev.fajar.hris

import dev.fajar.hris.collaboration.delivery.requests.matrixKeyUploadRequest
import dev.fajar.hris.collaboration.domain.policies.snapshotCollaborationKeyUpload
import dev.fajar.hris.core.domain.Result
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.json.JsonMapper

/**
 * Test-only counts/booleans. No key bytes, signatures, identity strings or request body survive.
 */
internal fun nativeSdkKeyShape(bytes: ByteArray): Map<String, Any> {
    if (bytes.size !in 1..65_536) return mapOf("parsed" to false)
    return try {
        val mapper =
            JsonMapper.builder(
                    JsonFactory.builder()
                        .streamReadConstraints(
                            StreamReadConstraints.builder()
                                .maxNestingDepth(8)
                                .maxStringLength(1024)
                                .build()
                        )
                        .build()
                )
                .build()
        val request = matrixKeyUploadRequest(mapper.readTree(bytes))
        mapOf(
            "parsed" to true,
            "devicePresent" to (request.deviceKeys != null),
            "oneTimeCount" to request.oneTimeKeys.size,
            "fallbackCount" to request.fallbackKeys.size,
            "overlappingIds" to request.oneTimeKeys.keys.count { it in request.fallbackKeys },
            "oneTimeMarkedFallback" to request.oneTimeKeys.values.count { it.fallback == true },
            "fallbackNotMarked" to request.fallbackKeys.values.count { it.fallback != true },
            "shapeValid" to (snapshotCollaborationKeyUpload(request) is Result.Success),
        )
    } catch (_: Exception) {
        mapOf("parsed" to false)
    }
}
