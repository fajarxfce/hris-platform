package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobKind

enum class LeaveBatchKind(val permission: String, val jobKind: JobKind) {
    ACCRUAL("leave.accrual.post", JobKind.LEAVE_ACCRUAL),
    YEAR_CLOSE("leave.year.close", JobKind.LEAVE_YEAR_CLOSE),
}
