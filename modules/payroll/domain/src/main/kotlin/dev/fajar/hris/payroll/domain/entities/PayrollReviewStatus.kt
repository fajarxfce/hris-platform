package dev.fajar.hris.payroll.domain.entities

import java.time.*

enum class PayrollReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN,
}
