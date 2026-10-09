package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

class PayrollPayslipPdfHttpTest : PayrollFinalizationApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun identityKey(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_IDENTITY_KEYS") {
                "v1:" + java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 23 })
            }
        }
    }

    private fun download(
        browser: HttpClient,
        path: String,
        token: String? = null,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
    ): HttpResponse<ByteArray> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/pdf, application/problem+json")
                .method(method, HttpRequest.BodyPublishers.noBody())
        headers.forEach { (name, value) -> request.header(name, value) }
        if (token != null) request.header("Authorization", "Bearer $token")
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun published(f: PublicationFixture): String {
        assertTrue(stepFinalization(f, beginFinalization(f)) is Result.Success)
        val p = f.calculation.people.payroll
        return payrollBody(get(p.owner.client, "/api/v1/companies/${p.company}/payroll/payslips"))[
                "items"][0]["id"]
            .asString()
    }

    private fun failure(response: HttpResponse<ByteArray>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), String(response.body()))
        assertTrue(
            response
                .headers()
                .firstValue("Content-Type")
                .orElse("")
                .contains("application/problem+json")
        )
        assertEquals(code, json.readTree(response.body())["code"].asString())
        assertFalse(String(response.body()).startsWith("%PDF"))
    }

    @Test
    fun employeeAndFinanceReceiveTheSameFinalSnapshotWithExplicitPdfHeadersAndLanguages() {
        val f = approved()
        val p = f.calculation.people.payroll
        val id = published(f)
        val path = "/api/v1/companies/${p.company}/payroll/payslips/$id"
        val snapshot = payrollBody(get(p.owner.client, path))
        val english = download(p.owner.client, "$path/pdf?language=EN")
        assertEquals(200, english.statusCode(), String(english.body()))
        assertEquals("application/pdf", english.headers().firstValue("Content-Type").orElseThrow())
        assertEquals(
            english.body().size.toLong(),
            english.headers().firstValueAsLong("Content-Length").orElseThrow(),
        )
        assertTrue(
            english
                .headers()
                .firstValue("Content-Disposition")
                .orElseThrow()
                .contains("payslip-$id-en.pdf")
        )
        assertTrue(english.headers().allValues("Cache-Control").any { "no-store" in it })
        assertEquals(
            "nosniff",
            english.headers().firstValue("X-Content-Type-Options").orElseThrow(),
        )
        assertTrue(String(english.body().take(8).toByteArray()).startsWith("%PDF-"))
        Loader.loadPDF(english.body()).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains(snapshot["companyName"].asString()), text)
            assertTrue(text.contains(snapshot["summary"]["employeeName"].asString()), text)
            assertTrue(text.contains("Take-home pay"), text)
            assertTrue(text.contains(id), text)
        }
        assertArrayEquals(
            english.body(),
            download(p.operator.client, "$path/pdf?language=EN").body(),
        )
        val etag = english.headers().firstValue("ETag").orElseThrow()
        val partial =
            download(
                p.owner.client,
                "$path/pdf?language=EN",
                headers = mapOf("Range" to "bytes=0-31", "If-Range" to etag),
            )
        assertEquals(206, partial.statusCode(), String(partial.body()))
        assertEquals(
            "bytes 0-31/${english.body().size}",
            partial.headers().firstValue("Content-Range").orElseThrow(),
        )
        assertArrayEquals(english.body().copyOfRange(0, 32), partial.body())
        val unchanged =
            download(
                p.owner.client,
                "$path/pdf?language=EN",
                headers = mapOf("If-None-Match" to etag),
            )
        assertEquals(304, unchanged.statusCode(), String(unchanged.body()))
        assertEquals(0, unchanged.body().size)
        val head = download(p.owner.client, "$path/pdf?language=EN", method = "HEAD")
        assertEquals(200, head.statusCode(), String(head.body()))
        assertEquals(0, head.body().size)
        assertEquals(
            english.body().size.toLong(),
            head.headers().firstValueAsLong("Content-Length").orElseThrow(),
        )
        assertEquals(
            416,
            download(
                    p.owner.client,
                    "$path/pdf?language=EN",
                    headers = mapOf("Range" to "bytes=9999999-"),
                )
                .statusCode(),
        )
        val indonesian = download(p.owner.client, "$path/pdf")
        assertEquals(200, indonesian.statusCode(), String(indonesian.body()))
        Loader.loadPDF(indonesian.body()).use { document ->
            assertTrue(PDFTextStripper().getText(document).contains("Gaji bersih"))
        }
        database()
            .update(
                "update companies set name='Later company name',version=version+1 where id=?",
                p.company,
            )
        assertArrayEquals(english.body(), download(p.owner.client, "$path/pdf?language=EN").body())
        failure(download(p.owner.client, "$path/pdf?language=unsupported"), 400, "invalid_request")
    }

    @Test
    fun nativeBearerDownloadsHonorTheSameOwnershipAndCompanyBoundaries() {
        val f = approved()
        val p = f.calculation.people.payroll
        val id = published(f)
        val path = "/api/v1/companies/${p.company}/payroll/payslips/$id/pdf"
        val exchanged =
            command(
                p.owner.client,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Payslip test"}""",
                p.owner.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchanged.statusCode(), exchanged.body())
        val token = json.readTree(exchanged.body())["accessToken"].asString()
        val native = download(client(), path, token)
        assertEquals(200, native.statusCode(), String(native.body()))
        assertArrayEquals(download(p.owner.client, path).body(), native.body())
        failure(download(client(), path), 401, "authentication_required")
        failure(download(p.admin, path), 403, "access_denied")
        val unbound = payrollMember(p.company, setOf("company.read", "payroll.self.read"))
        failure(download(unbound.client, path), 404, "payroll_payslip_not_found")
        val foreign = payrollFixture()
        failure(download(foreign.owner.client, path), 403, "company_access_denied")
        failure(
            download(
                foreign.operator.client,
                "/api/v1/companies/${foreign.company}/payroll/payslips/$id/pdf",
            ),
            404,
            "payroll_payslip_not_found",
        )
    }

    @Test
    fun revocationDuringPendingDownloadReturnsALocalizableProblemBeforeAnyPdfBytes() {
        val f = approved()
        val p = f.calculation.people.payroll
        val id = published(f)
        val path = "/api/v1/companies/${p.company}/payroll/payslips/$id/pdf"
        for (kind in listOf("permission", "membership", "credentials")) {
            val reader = payrollMember(p.company, setOf("company.read", "payroll.read"))
            val barrier = AccountLockProbe.Barrier(reader.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<HttpResponse<ByteArray>> {
                        download(reader.client, path, headers = mapOf("If-None-Match" to "*"))
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (kind) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.read'",
                                    p.company,
                                    reader.account,
                                )
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    p.company,
                                    reader.account,
                                )
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    reader.account,
                                )
                    }
                    barrier.release.countDown()
                    failure(
                        pending.get(10, TimeUnit.SECONDS),
                        if (kind == "credentials") 401 else 403,
                        when (kind) {
                            "permission" -> "access_denied"
                            "membership" -> "company_access_denied"
                            else -> "session_revoked"
                        },
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        }
    }
}
