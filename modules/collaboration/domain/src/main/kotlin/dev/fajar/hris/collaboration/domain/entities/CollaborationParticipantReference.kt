package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Selection hint only; a conversation command must authorize the selected account again. */
data class CollaborationParticipantReference(val accountId: UUID, val displayName: String)
