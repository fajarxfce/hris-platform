package dev.fajar.hris.workforce.data.models

import dev.fajar.hris.schema.tables.records.AttendanceEventsRecord
import dev.fajar.hris.schema.tables.records.AttendanceReviewsRecord

data class AttendanceRow(val event: AttendanceEventsRecord, val review: AttendanceReviewsRecord?)
