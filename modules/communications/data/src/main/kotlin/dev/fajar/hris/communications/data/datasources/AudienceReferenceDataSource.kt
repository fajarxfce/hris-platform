package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.communications.data.models.*

interface AudienceReferenceDataSource {
    fun units(query: AudienceReferenceQuery, kind: String): List<AudienceReferenceRow>

    fun groups(query: AudienceReferenceQuery): List<AudienceReferenceRow>

    fun employments(query: AudienceReferenceQuery): List<AudienceReferenceRow>
}
