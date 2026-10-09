package dev.fajar.hris.payroll.domain.entities

import java.time.*

enum class PayrollReviewAction {
    SUBMITTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
}
