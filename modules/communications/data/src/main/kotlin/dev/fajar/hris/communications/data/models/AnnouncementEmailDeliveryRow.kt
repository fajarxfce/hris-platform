package dev.fajar.hris.communications.data.models

data class AnnouncementEmailDeliveryRow(
    val pending: Int,
    val processing: Int,
    val accepted: Int,
    val failed: Int,
    val superseded: Int,
)
