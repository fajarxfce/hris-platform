package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomRequestFence
import java.util.UUID

fun validCollaborationSpaceRoomRequestFence(value: CollaborationSpaceRoomRequestFence): Boolean =
    value.accountId != UUID(0, 0) &&
        value.id != UUID(0, 0) &&
        validCollaborationSpaceRoomReceiptDescriptor(value.original) &&
        value.createdAt.epochSecond in -62_135_596_800L..253_402_300_799L &&
        value.createdAt.nano % 1000 == 0
