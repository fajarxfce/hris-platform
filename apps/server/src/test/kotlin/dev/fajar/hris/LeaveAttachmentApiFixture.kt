package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.leave.domain.usecases.ReadLeaveAttachmentContent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(LeaveAttachmentProbeConfiguration::class, AccountLockProbeConfiguration::class)
abstract class LeaveAttachmentApiFixture : LeaveApiFixture() {
    @Autowired protected lateinit var attachmentProbe: LeaveAttachmentProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var readAttachment: ReadLeaveAttachmentContent
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var runtimeJdbc: org.springframework.jdbc.core.JdbcTemplate

    @AfterEach
    fun clearAttachmentProbes() {
        attachmentProbe.clear()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    protected fun preparedLeave(): LeaveFixture {
        val f = leaveFixture(Instant.now().truncatedTo(ChronoUnit.SECONDS))
        configureWorkAndApprovals(f)
        body(adjust(f, "5"))
        return f
    }

    protected fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun error(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body()).get("code").asString())
    }

    protected fun documentFixture(f: LeaveFixture, employee: UUID = f.employee): Fixture {
        val id =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        return Fixture(
            f.admin,
            f.adminCsrf,
            Actor(
                id,
                f.company,
                PermissionCatalog.companyAdministrator,
                clock.instant(),
                UUID.randomUUID(),
            ),
            employee,
        )
    }

    protected fun evidence(
        f: LeaveFixture,
        bytes: ByteArray = pdf,
        employee: UUID = f.employee,
        document: UUID = UUID.randomUUID(),
        version: Long = 0,
        classification: String = "PERSONAL",
        ready: Boolean = true,
    ): UUID {
        val doc = documentFixture(f, employee)
        val revisionId = UUID.randomUUID()
        body(start(doc, uploadInput(doc, bytes, document, revisionId, version, classification)))
        if (!ready) return revisionId
        for (offset in bytes.indices step 1048576) body(
            upload(
                doc,
                revisionId,
                bytes.copyOfRange(offset, minOf(bytes.size, offset + 1048576)),
                offset.toLong(),
            )
        )
        val lease = beginValidation(doc, revisionId)
        repeat(105) {
            val step = run(doc, lease)
            assertTrue(step is Result.Success, step.toString())
            if (jobStatus(lease) == "SUCCEEDED") {
                assertEquals("READY", revision(doc, revisionId).get("status").asString())
                return revisionId
            }
        }
        fail<Unit>("Document fixture exceeded its bounded validation steps")
        return revisionId
    }

    protected fun submitEvidence(
        f: LeaveFixture,
        id: UUID = UUID.randomUUID(),
        revisions: List<UUID>,
        key: UUID = UUID.randomUUID(),
        day: String = "2026-10-05",
    ) = submit(f, id, listOf(day to "FULL"), key, revisions)

    protected fun actor(f: LeaveFixture) =
        Actor(
            f.account,
            f.company,
            setOf("company.read", "leave.self.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
        )

    protected fun download(
        f: LeaveFixture,
        requestId: UUID,
        revisionId: UUID,
        browser: HttpClient = f.worker,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
    ): HttpResponse<ByteArray> {
        val request =
            HttpRequest.newBuilder(
                    URI(
                        "http://127.0.0.1:$port/api/v1/companies/${f.company}/leave/requests/$requestId/attachments/$revisionId/content"
                    )
                )
                .timeout(Duration.ofSeconds(15))
        headers.forEach { (name, value) -> request.header(name, value) }
        return browser.send(
            request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
    }

    protected fun referenceCount(f: LeaveFixture, id: UUID) =
        database()
            .queryForObject(
                "select count(*) from document_evidence_references where company_id=? and source_kind='LEAVE_REQUEST' and source_id=?",
                Int::class.java,
                f.company,
                id,
            )!!

    protected fun member(f: LeaveFixture, permissions: List<String>): Pair<UUID, HttpClient> {
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Leave attachment member',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    account,
                    it,
                )
        }
        val browser = client()
        login(browser, "$account@example.test")
        return account to browser
    }
}
