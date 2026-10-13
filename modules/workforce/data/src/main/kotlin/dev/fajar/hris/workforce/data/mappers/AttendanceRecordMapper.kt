package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.schema.tables.records.AttendanceSyncHeadsRecord
import dev.fajar.hris.workforce.domain.entities.AttendanceDayHead

fun AttendanceSyncHeadsRecord.toHead(): AttendanceDayHead =
    AttendanceDayHead(
        requireNotNull(id),
        requireNotNull(employmentId),
        requireNotNull(workDate),
        requireNotNull(version),
    )
