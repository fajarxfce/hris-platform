package dev.fajar.hris.reporting.data

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.ReportExportDataSource
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.data.repositories.StoredReportExportRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import tools.jackson.module.kotlin.jacksonObjectMapper

class ReportExportRepositoryTest {
    private val json = jacksonObjectMapper()
    private val at = Instant.parse("2026-10-01T00:00:00.123456Z")
    private val company = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val request =
        (validateReportExportRequest(
                ReportExportRequest(
                    id,
                    company,
                    account,
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
            )
                as Result.Success)
            .value
    private val original = request.toExportRow(UUID.randomUUID(), json)

    private inner class Source : ReportExportDataSource {
        var found: ReportExportRow? = original
        var listed = listOf(original)
        var accepted = emptyList<ReportExportPartRow>()
        var failure: Exception? = null
        var writes = 0

        private fun write(): Int {
            failure?.let { throw it }
            writes++
            return 1
        }

        override fun lockOwner(company: UUID, account: UUID) {
            failure?.let { throw it }
        }

        override fun quota(company: UUID, account: UUID) = ReportExportQuotaRow(1, 1)

        override fun create(row: ReportExportRow): Int {
            found = row
            return write()
        }

        override fun find(company: UUID, account: UUID, id: UUID, lock: Boolean): ReportExportRow? {
            failure?.let { throw it }
            return found
        }

        override fun list(
            company: UUID,
            account: UUID,
            beforeAt: Instant?,
            beforeId: UUID?,
            limit: Int,
        ) = listed

        override fun captured(company: UUID, account: UUID, id: UUID, version: Long, at: Instant) =
            write()

        override fun reserve(row: ReportExportUploadRow, version: Long) = write()

        override fun accept(row: ReportExportPartRow, version: Long, at: Instant) = write()

        override fun parts(company: UUID, account: UUID, id: UUID, limit: Int) =
            accepted.also { assertEquals(1025, limit) }

        override fun publish(
            company: UUID,
            account: UUID,
            id: UUID,
            version: Long,
            tag: String,
            at: Instant,
        ) = write()

        override fun stop(
            company: UUID,
            account: UUID,
            id: UUID,
            version: Long,
            code: String,
            at: Instant,
        ) = write()

        override fun resume(
            company: UUID,
            account: UUID,
            id: UUID,
            version: Long,
            job: UUID,
            at: Instant,
        ) = write()

        override fun retire(company: UUID, account: UUID, id: UUID, version: Long, at: Instant) =
            write()
    }

    private val source = Source()
    private val repository = StoredReportExportRepository(source, json)

    private fun found() = (repository.find(company, account, id) as Result.Success).value!!

    @Test
    fun canonicalRequestAndLifecycleMetadataRoundTripWithoutRestoringExtraAuthority() {
        assertEquals(request, found().request)
        assertThrows(UnsupportedOperationException::class.java) {
            (found().request.sources as MutableMap).clear()
        }
        assertEquals(Result.Success(true), repository.create(request, UUID.randomUUID()))
        assertEquals(request, found().request)
        for (row in
            listOf(
                original.copy(company = UUID.randomUUID()),
                original.copy(account = UUID.randomUUID()),
                original.copy(id = UUID.randomUUID()),
                original.copy(sources = "{}"),
                original.copy(sources = original.sources.replace("people.read", "people.manage")),
                original.copy(client = "{\"platform\":null,\"build\":1,\"native\":false}"),
                original.copy(client = "{\"platform\":\"WEB\",\"build\":1.5,\"native\":false}"),
                original.copy(attempt = 9),
                original.copy(status = "READY"),
                original.copy(reservedBytes = 1),
                original.copy(columns = "[]"),
            )) {
            source.found = row
            assertInstanceOf(Result.Failed::class.java, repository.find(company, account, id))
        }
        source.found = null
        assertEquals(Result.Success(null), repository.find(company, account, id))
    }

    @Test
    fun invalidUploadShapesAndScopeNeverWriteAndLostAcquisitionPreservesCancellation() {
        val queued = found()
        val current = queued.copy(status = ReportExportStatus.RENDERING, snapshotId = id)
        val p =
            ReportExportUpload(
                UUID.randomUUID(),
                company,
                account,
                id,
                1,
                0,
                1,
                0,
                100,
                "a".repeat(64),
                at,
            )
        for (upload in
            listOf(
                p.copy(accountId = UUID.randomUUID()),
                p.copy(exportId = UUID.randomUUID()),
                p.copy(offset = 1),
                p.copy(bytes = 1048577),
                p.copy(createdAt = at.minusSeconds(1)),
            )) assertInstanceOf(Result.Failed::class.java, repository.reserve(current, upload))
        assertEquals(0, source.writes)
        source.failure =
            DataAccessResourceFailureException(
                "private connection",
                java.sql.SQLException("private", "08006"),
            )
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy")),
            repository.find(company, account, id),
        )
        source.failure = CancellationException()
        assertThrows(CancellationException::class.java) { repository.find(company, account, id) }
        source.failure = InterruptedException()
        assertThrows(InterruptedException::class.java) { repository.reserve(current, p) }
    }

    @Test
    fun keysetPagesRejectWrongOrderOversizedSourcesAndRepeatedRows() {
        val page = (repository.list(company, account, null, 51) as Result.Success).value
        assertThrows(UnsupportedOperationException::class.java) { (page as MutableList).clear() }
        assertInstanceOf(Result.Failed::class.java, repository.list(company, account, null, 52))
        assertInstanceOf(
            Result.Failed::class.java,
            repository.list(company, account, ReportExportCursor(Instant.MAX, id), 10),
        )
        for (rows in
            listOf(
                List(52) { original },
                listOf(original, original),
                listOf(
                    original,
                    original.copy(
                        id = UUID.randomUUID(),
                        createdAt = at.plusSeconds(1),
                        updatedAt = at.plusSeconds(1),
                    ),
                ),
            )) {
            source.listed = rows
            assertInstanceOf(Result.Failed::class.java, repository.list(company, account, null, 51))
        }
        source.listed = listOf(original)
        assertInstanceOf(
            Result.Failed::class.java,
            repository.list(company, account, ReportExportCursor(at, id), 10),
        )
    }

    @Test
    fun acceptedPartsMustFormADetachedCompletePrefixInTheRequestedOwnerScope() {
        val p =
            ReportExportPart(
                ReportExportUpload(
                    UUID.randomUUID(),
                    company,
                    account,
                    id,
                    1,
                    0,
                    1,
                    0,
                    100,
                    "a".repeat(64),
                    at,
                ),
                "etag",
            )
        source.accepted = listOf(p.toExportPartRow())
        val parts = (repository.parts(company, account, id) as Result.Success).value
        assertEquals(listOf(p), parts)
        assertThrows(UnsupportedOperationException::class.java) { (parts as MutableList).clear() }
        for (rows in
            listOf(
                List(1025) { p.toExportPartRow() },
                listOf(p.toExportPartRow(), p.toExportPartRow()),
                listOf(
                    p.copy(upload = p.upload.copy(companyId = UUID.randomUUID())).toExportPartRow()
                ),
                listOf(
                    p.copy(upload = p.upload.copy(number = 2, afterRow = 1, offset = 100))
                        .toExportPartRow()
                ),
            )) {
            source.accepted = rows
            assertInstanceOf(Result.Failed::class.java, repository.parts(company, account, id))
        }
    }
}
