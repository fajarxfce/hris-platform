package dev.fajar.hris.reporting.delivery.mappers

import dev.fajar.hris.reporting.delivery.responses.HeadcountCountsResponse
import dev.fajar.hris.reporting.domain.entities.HeadcountCounts

fun HeadcountCounts.toResponse() =
    HeadcountCountsResponse(
        employments,
        persons,
        active,
        probation,
        suspended,
        permanent,
        fixedTerm,
    )
