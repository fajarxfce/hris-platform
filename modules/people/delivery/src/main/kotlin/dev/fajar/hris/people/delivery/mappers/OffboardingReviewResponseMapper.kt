package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.OffboardingReviewResponse
import dev.fajar.hris.people.domain.entities.OffboardingReview

fun OffboardingReview.toResponse() =
    OffboardingReviewResponse(case.toResponse(), employmentVersion, today)
