package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.ReportSnapshotSourceRepository
import dev.fajar.hris.reporting.domain.usecases.ReadReportExports
import java.net.URI
import java.net.http.*
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ReportExportReadProbeConfiguration::class)
class ReportExportHttpTest : ReportExportApiFixture() {
    @Autowired private lateinit var probe: ReportExportReadProbe
    @Autowired
    private lateinit var expiry:
        dev.fajar.hris.reporting.domain.repositories.ReportExportExpiryRepository
    @Autowired private lateinit var reads: ReadReportExports

    private fun base(f: Fixture) = "/api/v1/companies/${f.company}/reports/exports"

    private fun body(
        kind: ReportKind = ReportKind.HEADCOUNT,
        dataset: ReportExportDataset = ReportExportDataset.SUMMARY,
    ): String {
        val input = selection(kind)
        return json.writeValueAsString(
            mapOf(
                "kind" to kind,
                "definitionVersion" to input.definitionVersion,
                "parameters" to input.parameters,
                "dataset" to dataset,
            )
        )
    }

    private fun submit(
        f: Fixture,
        key: UUID = UUID.randomUUID(),
        kind: ReportKind = ReportKind.HEADCOUNT,
        dataset: ReportExportDataset = ReportExportDataset.SUMMARY,
    ) = command(f.browser, base(f), body(kind, dataset), f.csrf, key)

    private fun id(response: HttpResponse<String>): UUID {
        assertEquals(200, response.statusCode(), response.body())
        return UUID.fromString(json.readTree(response.body())["id"].asString())
    }

    private fun binary(
        f: Fixture,
        id: UUID,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<ByteArray> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${base(f)}/$id/content"))
                .timeout(Duration.ofSeconds(30))
                .method(method, HttpRequest.BodyPublishers.noBody())
        headers.forEach { (name, value) -> request.header(name, value) }
        return f.browser.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun ready(f: Fixture): UUID {
        val id = id(submit(f))
        finish(f, id, worker(), lease(f, id))
        return id
    }

    @AfterEach
    fun resetStorage() {
        probe.afterRead = {}
        probe.modify = { it }
        probe.reads = 0
        probe.objects.clear()
    }

    @Test
    fun runtimeCannotDiscoverOrDisableEvenItsOwnExpiredCleanupSchedule() {
        val f = exportFixture()
        val original = id(submit(f))
        val template = find(f, original).request
        val expired =
            template.copy(
                id = UUID.randomUUID(),
                createdAt = clock.instant().minusSeconds(120),
                expiresAt = clock.instant().minusSeconds(60),
            )
        val job =
            dev.fajar.hris.jobs.domain.entities.JobRequest(
                UUID.randomUUID(),
                f.company,
                f.account,
                dev.fajar.hris.jobs.domain.entities.JobKind.REPORT_EXPORT,
                UUID.randomUUID(),
                mapOf("exportId" to expired.id.toString()),
                f.actor.authenticatedAt,
                requireNotNull(f.actor.credentialVersion),
                UUID.randomUUID(),
                expired.createdAt,
                REPORT_EXPORT_JOB_STEPS,
                dev.fajar.hris.jobs.domain.entities.JobProgressMode.UPPER_BOUND,
            )
        value(
            transactions.run(actor(f)) {
                jobs.create(job).flatMap { exports.create(expired, job.id) }
            }
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from report_export_expiry_queue where export_id=? and due_at<=clock_timestamp()",
                    Int::class.java,
                    expired.id,
                ),
        )
        val key =
            ReportExportExpiryKey(
                f.company,
                f.account,
                expired.id,
                expired.expiresAt,
                expired.expiresAt,
            )
        assertTrue(value(transactions.run(actor(f)) { expiry.due(clock.instant(), 4) }).isEmpty())
        assertEquals(
            Result.Success(false),
            transactions.run(actor(f)) { expiry.retry(key, clock.instant().plusSeconds(60)) },
        )
        assertTrue(transactions.run(actor(f)) { expiry.remove(key) } is Result.Failed)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from report_export_expiry_queue where export_id=?",
                    Int::class.java,
                    expired.id,
                ),
        )
    }

    @Test
    fun metadataAndContentRecheckMfaAfterAcquisition() {
        val f = exportFixture()
        val id = ready(f)
        val at = clock.instant()
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val secured = actor(f).copy(mfaVerifiedAt = at)
        fun reader(
            repository: dev.fajar.hris.reporting.domain.repositories.ReportExportRepository =
                exports
        ) =
            ReadReportExports(
                repository,
                snapshots,
                jobs,
                policies,
                companies,
                members,
                identities,
                probe,
                transactions,
                sources,
                security.copy(enforceMfa = true),
                clock,
            )
        val content = value(reader().download(secured, id, browserClient))
        probe.afterRead = { clock.set(at.plusSeconds(43200)) }
        rejected(reader().read(secured, id, content.tag, 0, 1, browserClient), "mfa_required")
        probe.afterRead = {}
        clock.set(at)
        val delayed =
            object :
                dev.fajar.hris.reporting.domain.repositories.ReportExportRepository by exports {
                override fun list(
                    company: UUID,
                    account: UUID,
                    before: ReportExportCursor?,
                    limit: Int,
                ): Result<List<ReportExport>> =
                    exports.list(company, account, before, limit).map {
                        clock.set(at.plusSeconds(43200))
                        it
                    }
            }
        rejected(reader(delayed).list(secured), "mfa_required")
    }

    @Test
    fun admissionReplaysOwnerStatusAndCleanupRemainAvailableWithoutSourceOrModuleAccess() {
        val f = exportFixture()
        val key = UUID.randomUUID()
        val id = id(submit(f, key))
        assertNotEquals(key, id)
        assertEquals(id, id(submit(f, key)))
        val response = get(f.browser, "${base(f)}/$id")
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        val detail = json.readTree(response.body())
        assertEquals("QUEUED", detail["status"].asString())
        for (name in
            listOf(
                "sources",
                "client",
                "accountId",
                "objectKey",
                "sha256",
                "manifestTag",
            )) assertFalse(detail.has(name), name)
        revoke(f, "people.read")
        assertEquals(
            200,
            savePolicy(f, changes = mapOf("disabledModules" to listOf("REPORTING"))).statusCode(),
        )
        assertEquals(200, get(f.browser, base(f)).statusCode())
        assertEquals(200, get(f.browser, "${base(f)}/$id").statusCode())
        val cancelKey = UUID.randomUUID()
        val cancel =
            command(
                f.browser,
                "${base(f)}/$id/cancel",
                "{\"expectedVersion\":0}",
                f.csrf,
                cancelKey,
            )
        assertEquals(200, cancel.statusCode(), cancel.body())
        assertEquals(
            cancel.body(),
            command(
                    f.browser,
                    "${base(f)}/$id/cancel",
                    "{\"expectedVersion\":0}",
                    f.csrf,
                    cancelKey,
                )
                .body(),
        )
        assertTrue(binary(f, id).statusCode() >= 400)
        revoke(f, "reports.read")
        assertEquals(403, get(f.browser, "${base(f)}/$id").statusCode())
        assertEquals(403, get(f.browser, base(f)).statusCode())
    }

    @Test
    fun privateHistoryUsesStableKeysetsAndDoesNotShareAnExportWithAnotherMember() {
        val f = exportFixture()
        val first = ready(f)
        val second = ready(f)
        val page = get(f.browser, "${base(f)}?size=1")
        assertEquals(200, page.statusCode(), page.body())
        val parsed = json.readTree(page.body())
        assertEquals(1, parsed["items"].size())
        val cursor = parsed["next"]
        val next =
            get(
                f.browser,
                "${base(f)}?size=1&beforeAt=${cursor["createdAt"].asString()}&beforeId=${cursor["id"].asString()}",
            )
        assertEquals(200, next.statusCode(), next.body())
        assertEquals(
            setOf(first.toString(), second.toString()),
            setOf(
                parsed["items"][0]["id"].asString(),
                json.readTree(next.body())["items"][0]["id"].asString(),
            ),
        )
        for (query in listOf("size=51", "size=0", "beforeId=$first")) assertEquals(
            422,
            get(f.browser, "${base(f)}?$query").statusCode(),
        )
        val g = exportFixture()
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                g.account,
            )
        for (permission in listOf("reports.read", "people.read")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                g.account,
                permission,
            )
        assertEquals(404, get(g.browser, "${base(f)}/$first").statusCode())
        assertEquals(0, json.readTree(get(g.browser, base(f)).body())["items"].size())
    }

    @Test
    fun csvRangesCrossSeveralPartsAndRetainStrongConditionalAndHeadSemantics() {
        val f = exportFixture()
        val id = id(submit(f, kind = ReportKind.LEAVE, dataset = ReportExportDataset.DETAIL))
        val generated =
            object : ReportSnapshotSourceRepository {
                override fun captureDetails(
                    capture: ReportSnapshotCapture
                ): Result<ReportCaptureTotals> {
                    val prefix = reportExportPrefixCells(capture)
                    for (first in 0 until 1201 step 300) {
                        val batch =
                            (first until minOf(first + 300, 1201)).map { row ->
                                prefix +
                                    capture.columns.drop(prefix.size).map { col ->
                                        when (col.type) {
                                            ReportExportColumnType.TEXT ->
                                                ReportExportCell.Text("row-$row")
                                            ReportExportColumnType.INTEGER ->
                                                ReportExportCell.Integer(row.toLong())
                                            ReportExportColumnType.DECIMAL ->
                                                ReportExportCell.Decimal(
                                                    java.math.BigDecimal("0.5")
                                                )
                                        }
                                    }
                            }
                        val added = snapshots.append(capture, first, batch)
                        if (added is Result.Failed) return added
                    }
                    return Result.Success(ReportCaptureTotals(1201, 0))
                }
            }
        finish(f, id, worker(details = generated), lease(f, id))
        val p = parts(f, id)
        assertEquals(3, p.size)
        val expected =
            p.flatMap {
                    probe.objects.getValue(reportExportObjectKey(it.upload)).second.asIterable()
                }
                .toByteArray()
        val full = binary(f, id)
        assertEquals(200, full.statusCode())
        assertArrayEquals(expected, full.body())
        assertTrue(full.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"))
        val tag = full.headers().firstValue("ETag").orElseThrow()
        val range =
            binary(
                f,
                id,
                headers = mapOf("Range" to "bytes=5-${expected.size-6}", "If-Range" to tag),
            )
        assertEquals(206, range.statusCode())
        assertArrayEquals(expected.copyOfRange(5, expected.size - 5), range.body())
        assertEquals(
            200,
            binary(f, id, headers = mapOf("Range" to "bytes=1-2", "If-Range" to "wrong"))
                .statusCode(),
        )
        val before = probe.reads
        assertEquals(304, binary(f, id, headers = mapOf("If-None-Match" to tag)).statusCode())
        assertEquals(
            416,
            binary(f, id, headers = mapOf("Range" to "bytes=${expected.size}-")).statusCode(),
        )
        val head = binary(f, id, "HEAD", mapOf("Range" to "bytes=1-2"))
        assertEquals(200, head.statusCode())
        assertEquals(0, head.body().size)
        assertEquals(
            expected.size.toString(),
            head.headers().firstValue("Content-Length").orElseThrow(),
        )
        assertEquals(before, probe.reads)
    }

    @Test
    fun providerIntegrityRevocationPolicyAndExpiryAreCheckedBeforeReleasingContent() {
        for (mode in listOf("permission", "credential", "expiry", "policy", "hash", "short")) {
            val f = exportFixture()
            val id = ready(f)
            val current = find(f, id)
            probe.afterRead = {
                when (mode) {
                    "permission" -> revoke(f, "people.read")
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    "expiry" -> clock.set(current.request.expiresAt)
                    "policy" ->
                        assertEquals(
                            200,
                            savePolicy(f, changes = mapOf("disabledModules" to listOf("REPORTING")))
                                .statusCode(),
                        )
                    else -> Unit
                }
            }
            probe.modify = { bytes ->
                when (mode) {
                    "hash" -> bytes.also { it[0] = (it[0].toInt() xor 1).toByte() }
                    "short" -> bytes.copyOf(bytes.size - 1)
                    else -> bytes
                }
            }
            val result = binary(f, id)
            assertTrue(result.statusCode() >= 400, "$mode returned ${result.statusCode()}")
            assertFalse(result.body().toString(Charsets.UTF_8).contains("definition_version"))
            probe.afterRead = {}
            probe.modify = { it }
            clock.set(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
        }
    }

    @Test
    fun metadataAndConditionalResponsesCannotReopenExpiredOrRevokedContent() {
        val f = exportFixture()
        val id = ready(f)
        val initial = binary(f, id)
        val tag = initial.headers().firstValue("ETag").orElseThrow()
        clock.set(find(f, id).request.expiresAt)
        val detail = json.readTree(get(f.browser, "${base(f)}/$id").body())
        assertTrue(detail["expired"].asBoolean())
        assertEquals(409, binary(f, id, "HEAD").statusCode())
        assertEquals(409, binary(f, id, headers = mapOf("If-None-Match" to tag)).statusCode())
        rejected(
            reads.read(actor(f), id, "invalid", 0, 1, browserClient),
            "invalid_report_export_range",
        )
    }
}
