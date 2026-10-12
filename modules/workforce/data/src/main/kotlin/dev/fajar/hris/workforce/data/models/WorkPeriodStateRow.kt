package dev.fajar.hris.workforce.data.models

import dev.fajar.hris.schema.tables.records.WorkPeriodsRecord

data class WorkPeriodStateRow(val period: WorkPeriodsRecord, val attempt: WorkPeriodAttemptRow?)
