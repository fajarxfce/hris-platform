package dev.fajar.hris.communications.data.models

data class AnnouncementDeliveryRow(
    val pending: Int,
    val processing: Int,
    val completed: Int,
    val failed: Int,
    val superseded: Int,
    val processed: Int,
    val accepted: Int,
    val rejected: Int,
)
