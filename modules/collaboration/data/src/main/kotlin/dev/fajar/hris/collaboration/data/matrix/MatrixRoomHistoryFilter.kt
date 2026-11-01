package dev.fajar.hris.collaboration.data.matrix

import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomHistoryFilter
import dev.fajar.hris.collaboration.domain.entities.validCollaborationRoomHistoryFilter

internal fun CollaborationRoomHistoryFilter.toMatrixFilter(): Map<String, Boolean> {
    require(validCollaborationRoomHistoryFilter(this))
    return if (!lazyLoadMembers) emptyMap()
    else mapOf("lazy_load_members" to true, "include_redundant_members" to includeRedundantMembers)
}
