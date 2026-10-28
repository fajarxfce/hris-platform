package dev.fajar.hris.reporting.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportExportPolicyTest {
    private val at = Instant.parse("2026-10-01T00:00:00.123456Z")
    private val company = UUID.randomUUID()
    private val request =
        ReportExportRequest(
            UUID.randomUUID(),
            company,
            UUID.randomUUID(),
            ReportSelection(
                ReportKind.HEADCOUNT,
                "headcount.v1",
                ReportFilterParameters(asOf = "2026-10-01"),
            ),
            ReportExportDataset.SUMMARY,
            mapOf(company to setOf("reports.read", "people.read")),
            ClientRequest(null, false),
            at,
            at.plusSeconds(3600),
        )

    private fun <T> value(result: Result<T>): T = (result as Result.Success).value

    private fun snapshot(rows: Int = 3): ReportSnapshot {
        val r = value(validateReportExportRequest(request))
        return ReportSnapshot(
            ReportSnapshotCapture(
                r.id,
                company,
                r.accountId,
                r.selection,
                r.dataset,
                value(reportExportColumns(r.selection.kind, r.dataset)),
                at,
                request.expiresAt,
            ),
            rows,
            if (rows == 0) 0 else 100,
        )
    }

    private fun part(number: Int = 1, after: Int = 0, rows: Int = 2, offset: Long = 0) =
        ReportExportPart(
            ReportExportUpload(
                UUID.randomUUID(),
                company,
                request.accountId,
                request.id,
                number,
                after,
                rows,
                offset,
                100,
                "a".repeat(64),
                at.plusSeconds(1),
            ),
            "provider-etag",
        )

    private fun queued() =
        ReportExport(
            value(validateReportExportRequest(request)),
            UUID.randomUUID(),
            ReportExportStatus.QUEUED,
            1,
            0,
            at,
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

    @Test
    fun sourceAuthorityIsNarrowDetachedAndIncludesTheAnchorWithoutChangingSelectedCompanies() {
        val selected = UUID.randomUUID()
        val permissions = mutableSetOf("reports.read", "people.read")
        val sources = mutableMapOf(company to permissions, selected to permissions)
        val r =
            request.copy(
                selection =
                    request.selection.copy(
                        parameters =
                            ReportFilterParameters(
                                asOf = "2026-10-01",
                                companies = listOf(selected),
                            )
                    ),
                sources = sources,
            )
        val normalized = value(validateReportExportRequest(r))
        permissions.clear()
        sources.clear()
        assertEquals(setOf(selected), reportExportCompanies(normalized))
        assertEquals(setOf(company, selected), normalized.sources.keys)
        assertEquals(setOf("reports.read", "people.read"), normalized.sources[company])
        assertThrows(UnsupportedOperationException::class.java) {
            (normalized.sources as MutableMap).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (normalized.sources[company] as MutableSet).clear()
        }
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportExportRequest(normalized.copy(sources = normalized.sources - selected)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportExportRequest(
                normalized.copy(
                    sources =
                        normalized.sources + (company to setOf("reports.read", "people.manage"))
                )
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportExportRequest(
                normalized.copy(
                    sources =
                        normalized.sources +
                            (company to setOf("reports.read", "people.read", "payroll.read"))
                )
            ),
        )
    }

    @Test
    fun onlyOriginalCanonicalCompanyWideAliasesAndCompatibleClientVersionsAreRetained() {
        for ((kind, grants) in
            listOf(
                ReportKind.OVERTIME to setOf("overtime.manage"),
                ReportKind.OVERTIME to setOf("workforce.read"),
                ReportKind.EXPENSES to setOf("expenses.manage"),
            )) {
            val r =
                request.copy(
                    selection =
                        ReportSelection(
                            kind,
                            kind.definitionVersion,
                            ReportFilterParameters(from = "2026-10-01", until = "2026-10-02"),
                        ),
                    sources = mapOf(company to (grants + "reports.read")),
                )
            assertInstanceOf(Result.Success::class.java, validateReportExportRequest(r))
            assertInstanceOf(
                Result.Failed::class.java,
                validateReportExportRequest(
                    r.copy(sources = mapOf(company to setOf("reports.read", "expenses.team.read")))
                ),
            )
        }
        for (client in
            listOf(
                ClientRequest(ClientVersion(ClientPlatform.WEB, 1), true),
                ClientRequest(ClientVersion(ClientPlatform.ANDROID, 1), false),
                ClientRequest(ClientVersion(ClientPlatform.WEB, -1), false),
            )) assertInstanceOf(
            Result.Failed::class.java,
            validateReportExportRequest(request.copy(client = client)),
        )
        assertInstanceOf(
            Result.Success::class.java,
            validateReportExportRequest(
                request.copy(
                    client = ClientRequest(ClientVersion(ClientPlatform.IOS, 999999999), true)
                )
            ),
        )
    }

    @Test
    fun exactLifetimeVersionsStatesAndReservationBudgetsRemainBounded() {
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportExportRequest(request.copy(expiresAt = at.plusSeconds(604801))),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportExportRequest(request.copy(createdAt = at.plusNanos(1))),
        )
        assertFalse(isReportExportInstant(Instant.MAX))
        assertFalse(isReportExportInstant(Instant.MIN))
        val q = queued()
        assertInstanceOf(Result.Success::class.java, validateReportExport(q))
        for (bad in
            listOf(
                q.copy(status = ReportExportStatus.READY),
                q.copy(attempt = 9),
                q.copy(version = 10000),
                q.copy(completedRows = 1),
                q.copy(reservationCount = 1),
                q.copy(updatedAt = Instant.MAX),
                q.copy(retiredAt = request.expiresAt),
            )) assertInstanceOf(Result.Failed::class.java, validateReportExport(bad))
        val rendering =
            q.copy(
                status = ReportExportStatus.RENDERING,
                snapshotId = request.id,
                reservationCount = 1088,
                reservedBytes = REPORT_EXPORT_MAX_RESERVED_BYTES,
            )
        assertInstanceOf(Result.Success::class.java, validateReportExport(rendering))
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportExport(rendering.copy(reservationCount = 1089)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportExport(
                rendering.copy(reservedBytes = REPORT_EXPORT_MAX_RESERVED_BYTES + 1)
            ),
        )
        val stopped =
            q.copy(
                status = ReportExportStatus.STOPPED,
                failureCode = "database_busy",
                finishedAt = at,
            )
        assertInstanceOf(Result.Success::class.java, validateReportExport(stopped))
        assertInstanceOf(
            Result.Failed::class.java,
            validateReportExport(stopped.copy(failureCode = "private details\n")),
        )
    }

    @Test
    fun manifestReconcilesOrderedRowsBytesAndProviderVersionsWithoutClaimingAFileHash() {
        val parts = listOf(part(), part(2, 2, 1, 100))
        val manifest = value(reportExportManifest(snapshot(), parts))
        assertEquals(3, manifest.rows)
        assertEquals(200, manifest.bytes)
        assertTrue(manifest.tag.matches(Regex("[0-9a-f]{64}")))
        assertEquals(manifest, value(reportExportManifest(snapshot(), parts.toList())))
        assertNotEquals(
            manifest.tag,
            value(
                    reportExportManifest(
                        snapshot(),
                        listOf(parts[0], parts[1].copy(etag = "new-version")),
                    )
                )
                .tag,
        )
        assertNotEquals(
            manifest.tag,
            value(
                    reportExportManifest(
                        snapshot(),
                        listOf(
                            parts[0],
                            parts[1].copy(upload = parts[1].upload.copy(sha256 = "b".repeat(64))),
                        ),
                    )
                )
                .tag,
        )
        assertEquals(
            "$company/reports/${request.id}/${parts[0].upload.id}",
            reportExportObjectKey(parts[0].upload),
        )
    }

    @Test
    fun partialDuplicateForeignAndOutOfOrderManifestsCannotPublish() {
        val a = part()
        val b = part(2, 2, 1, 100)
        for (parts in
            listOf(
                emptyList(),
                listOf(a),
                listOf(b, a),
                listOf(a, a),
                listOf(a, b.copy(upload = b.upload.copy(id = a.upload.id))),
                listOf(a, b.copy(upload = b.upload.copy(accountId = UUID.randomUUID()))),
                listOf(a, b.copy(upload = b.upload.copy(offset = 101))),
                listOf(a, b.copy(upload = b.upload.copy(afterRow = 1))),
                listOf(a, b.copy(upload = b.upload.copy(createdAt = request.expiresAt))),
                listOf(a, b.copy(etag = "etag\r\nprivate")),
            )) assertInstanceOf(Result.Failed::class.java, reportExportManifest(snapshot(), parts))
        assertInstanceOf(
            Result.Failed::class.java,
            reportExportManifest(snapshot(), List(1025) { a }),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            reportExportManifest(snapshot().copy(storedBytes = -1), listOf(a, b)),
        )
    }

    @Test
    fun anEmptyExportHasOneHeaderPartAndEveryUploadRequiresFiniteProgressAndBytes() {
        val header = part(rows = 0)
        assertEquals(0, value(reportExportManifest(snapshot(0), listOf(header))).rows)
        assertInstanceOf(
            Result.Failed::class.java,
            reportExportManifest(snapshot(), listOf(header)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            reportExportManifest(snapshot(0), listOf(header, part(2, 0, 0, 100))),
        )
        for (upload in
            listOf(
                header.upload.copy(rows = 501),
                header.upload.copy(bytes = 1048577),
                header.upload.copy(bytes = 0),
                header.upload.copy(offset = 1),
                header.upload.copy(sha256 = "x".repeat(64)),
            )) assertInstanceOf(Result.Failed::class.java, validateReportExportUpload(upload))
    }
}
