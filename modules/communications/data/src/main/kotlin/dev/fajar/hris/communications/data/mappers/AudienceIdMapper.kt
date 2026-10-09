package dev.fajar.hris.communications.data.mappers

import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun decodeAudienceIds(value: JSONB, json: ObjectMapper, maximum: Int): List<UUID> {
    val node = json.readTree(value.data())
    require(node.isArray && node.size() <= maximum)
    val ids =
        node
            .iterator()
            .asSequence()
            .map {
                require(it.isString)
                UUID.fromString(it.asString())
            }
            .toList()
    require(ids.distinct().size == ids.size)
    return ids
}
