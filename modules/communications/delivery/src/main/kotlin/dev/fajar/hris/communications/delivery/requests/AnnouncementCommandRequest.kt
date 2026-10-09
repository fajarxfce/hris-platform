package dev.fajar.hris.communications.delivery.requests

data class AnnouncementCommandRequest(val expectedVersion: Long, val reason: String)
