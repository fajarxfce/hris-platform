package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.math.BigDecimal
import java.util.UUID
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

fun ReportSnapshotCapture.toSnapshotHeader(json: ObjectMapper): ReportSnapshotHeaderRow {
    val p = selection.parameters
    return ReportSnapshotHeaderRow(
        companyId,
        accountId,
        id,
        json.writeValueAsString(
            mapOf(
                "kind" to selection.kind.name,
                "definitionVersion" to selection.definitionVersion,
                "dataset" to dataset.name,
                "schemaVersion" to schemaVersion,
                "formatVersion" to formatVersion,
                "asOf" to p.asOf,
                "from" to p.from,
                "until" to p.until,
                "status" to p.status,
                "companies" to p.companies.map(UUID::toString),
            )
        ),
        json.writeValueAsString(
            columns.map {
                mapOf("key" to it.key, "type" to it.type.name, "nullable" to it.nullable)
            }
        ),
        evaluatedAt,
        expiresAt,
        null,
        null,
    )
}

fun ReportSnapshotHeaderRow.toReportSnapshotCapture(
    company: UUID,
    account: UUID,
    snapshotId: UUID,
    json: ObjectMapper,
): ReportSnapshotCapture {
    require(companyId == company && accountId == account && id == snapshotId)
    require(definition.length <= 32768 && columns.length <= 16384)
    val d = json.readTree(definition)
    require(
        d.isObject &&
            d.propertyNames().toSet() ==
                setOf(
                    "kind",
                    "definitionVersion",
                    "dataset",
                    "schemaVersion",
                    "formatVersion",
                    "asOf",
                    "from",
                    "until",
                    "status",
                    "companies",
                )
    )
    val selected = requireNotNull(d["companies"])
    require(selected.isArray && selected.size() <= 32)
    val c = json.readTree(columns)
    require(c.isArray && c.size() in 1..REPORT_CSV_MAX_COLUMNS)
    val capture =
        ReportSnapshotCapture(
            id,
            companyId,
            accountId,
            ReportSelection(
                ReportKind.valueOf(d.text("kind")),
                d.text("definitionVersion"),
                ReportFilterParameters(
                    d.optionalText("asOf"),
                    d.optionalText("from"),
                    d.optionalText("until"),
                    d.optionalText("status"),
                    selected
                        .iterator()
                        .asSequence()
                        .map {
                            require(it.isString)
                            UUID.fromString(it.asString())
                        }
                        .toList(),
                ),
            ),
            ReportExportDataset.valueOf(d.text("dataset")),
            c.iterator()
                .asSequence()
                .map {
                    require(
                        it.isObject &&
                            it.propertyNames().toSet() == setOf("key", "type", "nullable") &&
                            it["nullable"].isBoolean
                    )
                    ReportExportColumn(
                        it.text("key"),
                        ReportExportColumnType.valueOf(it.text("type")),
                        it["nullable"].asBoolean(),
                    )
                }
                .toList(),
            evaluatedAt,
            expiresAt,
            d.text("schemaVersion"),
            d.text("formatVersion"),
        )
    val validated = validateReportSnapshotCapture(capture)
    require(validated is Result.Success && validated.value == capture)
    return validated.value
}

fun ReportSnapshotHeaderRow.toReportSnapshot(
    company: UUID,
    account: UUID,
    snapshotId: UUID,
    json: ObjectMapper,
): ReportSnapshot {
    val capture = toReportSnapshotCapture(company, account, snapshotId, json)
    require(
        rows != null &&
            rows in 0..REPORT_SNAPSHOT_MAX_ROWS &&
            storedBytes != null &&
            storedBytes in 0..REPORT_SNAPSHOT_MAX_BYTES
    )
    require((rows == 0) == (storedBytes == 0L))
    return ReportSnapshot(capture, rows, storedBytes)
}

/** Numeric strings retain exact values independently of JSON parser floating-point settings. */
fun List<ReportExportCell>.toSnapshotCells(json: ObjectMapper): String =
    json.writeValueAsString(
        map {
            when (it) {
                ReportExportCell.Empty -> null
                is ReportExportCell.Text -> it.value
                is ReportExportCell.Integer -> it.value.toString()
                is ReportExportCell.Decimal -> it.value.toString()
            }
        }
    )

fun ReportSnapshotCellsRow.toReportSnapshotRow(
    columns: List<ReportExportColumn>,
    json: ObjectMapper,
): ReportSnapshotRow {
    require(number in 1..REPORT_SNAPSHOT_MAX_ROWS && cells.length <= REPORT_SNAPSHOT_PAGE_BYTES)
    val array = json.readTree(cells)
    require(array.isArray && array.size() == columns.size)
    val values =
        array.mapIndexed { index, cell ->
            if (cell.isNull) ReportExportCell.Empty
            else {
                require(cell.isString && cell.asString().length <= REPORT_CSV_MAX_CELL_CHARACTERS)
                val text = cell.asString()
                when (columns[index].type) {
                    ReportExportColumnType.TEXT -> ReportExportCell.Text(text)
                    ReportExportColumnType.INTEGER -> {
                        require(text.length <= 20)
                        ReportExportCell.Integer(text.toLong())
                    }
                    ReportExportColumnType.DECIMAL -> {
                        require(text.length <= 48)
                        ReportExportCell.Decimal(BigDecimal(text))
                    }
                }
            }
        }
    require(encodeReportCsvPart(columns, listOf(values), false) is Result.Success)
    return ReportSnapshotRow(number, values)
}

private fun JsonNode.text(key: String): String =
    requireNotNull(this[key]).also { require(it.isString) }.asString()

private fun JsonNode.optionalText(key: String): String? =
    requireNotNull(this[key]).let { if (it.isNull) null else text(key) }
