package dev.fajar.hris.reporting.domain.entities

data class ReportExportColumn(
    val key: String,
    val type: ReportExportColumnType,
    val nullable: Boolean = false,
)

enum class ReportExportColumnType {
    TEXT,
    INTEGER,
    DECIMAL,
}
