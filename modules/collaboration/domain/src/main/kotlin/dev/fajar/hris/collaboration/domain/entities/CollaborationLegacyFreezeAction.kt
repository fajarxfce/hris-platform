package dev.fajar.hris.collaboration.domain.entities

/** Metadata transition only; neither action proves SDK drain or grants provider authority. */
enum class CollaborationLegacyFreezeAction {
    FREEZE,
    REPLAY,
}
