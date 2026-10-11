package dev.fajar.hris.communications.delivery.mappers

import dev.fajar.hris.communications.delivery.responses.*
import dev.fajar.hris.communications.domain.entities.AnnouncementReview

fun AnnouncementReview.toResponse() =
    AnnouncementReviewResponse(
        announcement.toResponse(),
        publicationJob?.let {
            AnnouncementPublicationJobResponse(
                it.id,
                it.status,
                it.cancellationRequested,
                it.version,
                it.failureCode,
            )
        },
        availableActions,
        evaluatedAt,
    )
