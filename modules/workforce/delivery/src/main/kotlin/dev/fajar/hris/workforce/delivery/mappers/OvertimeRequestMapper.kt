package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.requests.OvertimeIntervalRequest
import dev.fajar.hris.workforce.domain.entities.OvertimeInterval

fun OvertimeIntervalRequest.toInterval(): OvertimeInterval =
    OvertimeInterval(startsAt, endsAt, breakMinutes)
