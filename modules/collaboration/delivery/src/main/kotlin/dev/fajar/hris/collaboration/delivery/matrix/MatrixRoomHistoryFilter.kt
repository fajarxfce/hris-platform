package dev.fajar.hris.collaboration.delivery.matrix

import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomHistoryFilter
import dev.fajar.hris.collaboration.domain.entities.validCollaborationRoomHistoryFilter
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

private val historyFilterParser =
    JsonMapper.builder(
            JsonFactory.builder()
                .streamReadConstraints(
                    StreamReadConstraints.builder()
                        .maxNestingDepth(2)
                        .maxStringLength(64)
                        .maxNumberLength(8)
                        .build()
                )
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build()
        )
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build()

/** Accept only the bounded membership-loading flags supported by the SDK history endpoints. */
internal fun matrixRoomHistoryFilter(value: String?): CollaborationRoomHistoryFilter? {
    if (value == null) return CollaborationRoomHistoryFilter()
    if (value.length !in 2..256) return null
    val node =
        try {
            historyFilterParser.readTree(value)
        } catch (_: JacksonException) {
            return null
        } ?: return null
    if (!node.isObject || node.size() > 2) return null
    if (node.propertyNames().any { it !in setOf("lazy_load_members", "include_redundant_members") })
        return null
    if (node.properties().any { !it.value.isBoolean }) return null
    return CollaborationRoomHistoryFilter(
            node.path("lazy_load_members").asBoolean(false),
            node.path("include_redundant_members").asBoolean(false),
        )
        .takeIf(::validCollaborationRoomHistoryFilter)
}
