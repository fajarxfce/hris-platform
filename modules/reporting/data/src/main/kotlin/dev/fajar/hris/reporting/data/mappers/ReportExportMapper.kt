package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

fun ReportExportRequest.toExportRow(job: UUID, json: ObjectMapper): ReportExportRow {
    val columns = (reportExportColumns(selection.kind, dataset) as Result.Success).value
    val header =
        ReportSnapshotCapture(
                id,
                companyId,
                accountId,
                selection,
                dataset,
                columns,
                createdAt,
                expiresAt,
                schemaVersion,
                formatVersion,
            )
            .toSnapshotHeader(json)
    return ReportExportRow(
        companyId,
        accountId,
        id,
        header.definition,
        header.columns,
        json.writeValueAsString(
            sources.toSortedMap().mapKeys { it.key.toString() }.mapValues { it.value.sorted() }
        ),
        json.writeValueAsString(
            mapOf(
                "platform" to client.version?.platform?.name,
                "build" to client.version?.build,
                "native" to client.native,
            )
        ),
        createdAt,
        expiresAt,
        job,
        "QUEUED",
        1,
        0,
        createdAt,
        null,
        0,
        0,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
    )
}

fun ReportExportRow.toReportExport(
    company: UUID,
    account: UUID,
    id: UUID?,
    json: ObjectMapper,
): ReportExport {
    require(this.company == company && this.account == account && (id == null || this.id == id))
    val header =
        ReportSnapshotHeaderRow(
            company,
            account,
            this.id,
            definition,
            columns,
            createdAt,
            expiresAt,
            null,
            null,
        )
    val capture = header.toReportSnapshotCapture(company, account, this.id, json)
    require(validateReportExportCapture(capture) is Result.Success)
    require(sources.length <= 16384 && client.length <= 256)
    val grants = json.readTree(sources)
    require(grants.isObject && grants.size() in 1..33)
    val selected =
        grants.properties().associate { entry ->
            require(entry.key.length == 36 && entry.value.isArray && entry.value.size() in 1..4)
            val key = UUID.fromString(entry.key)
            require(key.toString() == entry.key)
            val values =
                entry.value
                    .iterator()
                    .asSequence()
                    .map {
                        require(it.isString)
                        it.asString()
                    }
                    .toList()
            require(values.toSet().size == values.size)
            key to values.toSet()
        }
    val c = json.readTree(client)
    require(
        c.isObject &&
            c.propertyNames().toSet() == setOf("platform", "build", "native") &&
            c["native"].isBoolean
    )
    val platform = c["platform"]
    val build = c["build"]
    require(platform.isNull == build.isNull)
    val version =
        if (platform.isNull) null
        else {
            require(platform.isString && build.isInt)
            ClientVersion(ClientPlatform.valueOf(platform.asString()), build.asInt())
        }
    val request =
        ReportExportRequest(
            this.id,
            company,
            account,
            capture.selection,
            capture.dataset,
            selected,
            ClientRequest(version, c["native"].asBoolean()),
            createdAt,
            expiresAt,
            capture.schemaVersion,
            capture.formatVersion,
        )
    val export =
        ReportExport(
            request,
            job,
            ReportExportStatus.valueOf(status),
            attempt,
            this.version,
            updatedAt,
            snapshot,
            completedRows,
            partCount,
            byteCount,
            reservationCount,
            reservedBytes,
            failureCode,
            manifestTag,
            finishedAt,
            retiredAt,
        )
    val valid = validateReportExport(export)
    require(valid is Result.Success)
    return valid.value
}

fun ReportExportUpload.toExportUploadRow() =
    ReportExportUploadRow(
        id,
        companyId,
        accountId,
        exportId,
        number,
        afterRow,
        rows,
        offset,
        bytes,
        sha256,
        createdAt,
    )

fun ReportExportPart.toExportPartRow() = ReportExportPartRow(upload.toExportUploadRow(), etag)

fun ReportExportPartRow.toReportExportPart(
    company: UUID,
    account: UUID,
    id: UUID,
): ReportExportPart {
    val p = upload
    require(p.company == company && p.account == account && p.export == id)
    val result =
        ReportExportPart(
            ReportExportUpload(
                p.id,
                p.company,
                p.account,
                p.export,
                p.number,
                p.afterRow,
                p.rows,
                p.offset,
                p.bytes,
                p.sha256,
                p.createdAt,
            ),
            etag,
        )
    require(validateReportExportPart(result) is Result.Success)
    return result
}
