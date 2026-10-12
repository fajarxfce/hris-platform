package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.responses.OvertimePlanningReviewResponse
import dev.fajar.hris.workforce.domain.entities.OvertimePlanningReview

fun OvertimePlanningReview.toResponse(): OvertimePlanningReviewResponse =
    OvertimePlanningReviewResponse(
        employee.toResponse(),
        workDate,
        companyDate,
        timezone,
        schedule.toResponse(),
    )
