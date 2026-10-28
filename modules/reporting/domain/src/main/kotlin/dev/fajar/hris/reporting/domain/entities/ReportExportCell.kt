package dev.fajar.hris.reporting.domain.entities

import java.math.BigDecimal

/** Distinguish numbers from text so negative quantities remain numeric in CSV. */
sealed interface ReportExportCell {
    data class Text(val value: String) : ReportExportCell

    data class Integer(val value: Long) : ReportExportCell

    data class Decimal(val value: BigDecimal) : ReportExportCell

    data object Empty : ReportExportCell
}
