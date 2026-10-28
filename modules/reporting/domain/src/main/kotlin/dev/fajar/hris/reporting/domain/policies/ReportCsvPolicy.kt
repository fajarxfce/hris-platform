package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import java.io.ByteArrayOutputStream

const val REPORT_CSV_FORMAT_VERSION = "report-csv.v1"
const val REPORT_CSV_MAX_COLUMNS = 64
const val REPORT_CSV_MAX_PART_ROWS = 500
const val REPORT_CSV_MAX_PART_BYTES = 1024 * 1024
const val REPORT_CSV_MAX_CELL_CHARACTERS = 4096

private val spreadsheetIgnoredCategories =
    setOf(
        Character.FORMAT.toInt(),
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
    )

/**
 * UTF-8 RFC 4180 records. A header/BOM appears only in the first part; concatenated parts form one
 * file. Consumes a complete-row prefix within the byte ceiling. The caller advances only by the
 * returned row count, owns pagination and publishes no partial export.
 */
fun encodeReportCsvPart(
    columns: List<ReportExportColumn>,
    rows: List<List<ReportExportCell>>,
    includesHeader: Boolean,
): Result<ReportCsvPart> {
    if (
        columns.size !in 1..REPORT_CSV_MAX_COLUMNS ||
            columns.any { !it.key.matches(Regex("[a-z][a-z0-9_]{0,63}")) } ||
            columns.map { it.key }.toSet().size != columns.size
    )
        return csvFailure("invalid_report_export_columns")
    if (rows.size > REPORT_CSV_MAX_PART_ROWS || (!includesHeader && rows.isEmpty()))
        return csvFailure("invalid_report_export_rows")
    // The byte ceiling is independent of caller text; never preallocate from its size.
    val output = ByteArrayOutputStream(8192)
    if (includesHeader) {
        output.write(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        output.write(
            (columns.joinToString(",") { quoteCsv(it.key) } + "\r\n").toByteArray(Charsets.UTF_8)
        )
    }
    var consumed = 0
    for (row in rows) {
        if (row.size != columns.size) return csvFailure("invalid_report_export_rows")
        // Every encoded row is independently bounded before joining or UTF-8 conversion.
        val values = ArrayList<String>(columns.size)
        for ((index, cell) in row.withIndex()) {
            val column = columns[index]
            val value =
                when (cell) {
                    ReportExportCell.Empty ->
                        if (column.nullable) "" else return csvFailure("invalid_report_export_rows")
                    is ReportExportCell.Integer -> {
                        if (column.type != ReportExportColumnType.INTEGER)
                            return csvFailure("invalid_report_export_rows")
                        cell.value.toString()
                    }
                    is ReportExportCell.Decimal -> {
                        if (
                            column.type != ReportExportColumnType.DECIMAL ||
                                cell.value.precision() > 30 ||
                                cell.value.scale() !in -9..9
                        )
                            return csvFailure("invalid_report_export_rows")
                        cell.value.toPlainString()
                    }
                    is ReportExportCell.Text -> {
                        if (column.type != ReportExportColumnType.TEXT || !validCsvText(cell.value))
                            return csvFailure("invalid_report_export_rows")
                        quoteCsv(literalSpreadsheetText(cell.value))
                    }
                }
            values.add(value)
        }
        val bytes = (values.joinToString(",") + "\r\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > REPORT_CSV_MAX_PART_BYTES - output.size()) {
            if (consumed == 0) return csvFailure("report_export_part_too_large")
            break
        }
        output.write(bytes)
        consumed++
    }
    return Result.Success(ReportCsvPart(output.toByteArray(), consumed, includesHeader))
}

private fun csvFailure(code: String) = Result.Failed(Failure(FailureKind.VALIDATION, code))

private fun quoteCsv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

private fun validCsvText(value: String): Boolean {
    if (value.length > REPORT_CSV_MAX_CELL_CHARACTERS) return false
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character.isHighSurrogate()) {
            if (index + 1 == value.length || !value[index + 1].isLowSurrogate()) return false
            index += 2
        } else {
            if (character.isLowSurrogate() || (character.isISOControl() && character !in "\t\r\n"))
                return false
            index++
        }
    }
    return true
}

/** Quoting alone does not prevent spreadsheet evaluation of a textual cell. */
private fun literalSpreadsheetText(value: String): String {
    var index = 0
    var first: Int? = null
    while (index < value.length) {
        val point = value.codePointAt(index)
        val category = Character.getType(point)
        if (
            !Character.isWhitespace(point) &&
                !Character.isISOControl(point) &&
                !Character.isSpaceChar(point) &&
                category !in spreadsheetIgnoredCategories
        ) {
            first = point
            break
        }
        index += Character.charCount(point)
    }
    return if (
        first in listOf('='.code, '+'.code, '-'.code, '@'.code) ||
            value.firstOrNull() in listOf('\t', '\r', '\n')
    )
        "'$value"
    else value
}
