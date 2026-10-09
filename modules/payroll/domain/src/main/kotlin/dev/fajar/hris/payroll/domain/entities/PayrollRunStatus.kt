package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.*
import java.time.*

enum class PayrollRunStatus {
    PROCESSING,
    STOPPED,
    CALCULATED,
    FINALIZED,
    ABANDONED,
}
