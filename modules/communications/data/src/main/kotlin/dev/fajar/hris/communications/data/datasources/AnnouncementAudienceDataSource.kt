package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.*

interface AnnouncementAudienceDataSource {
    fun recipients(query: AnnouncementRecipientQuery, limit: Int): List<AnnouncementRecipientRow>
}
