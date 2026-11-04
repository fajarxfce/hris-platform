package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Safe native command receipt. Provider generation is independent of device version. */
data class CollaborationSpaceProviderRenewal(val deviceId: UUID, val providerGeneration: Long)
