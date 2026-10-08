package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.decideApproval
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class ApprovalHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var journal: ChangeJournalRepository

    private fun company(client: HttpClient, csrf: String): UUID {
        val response =
            command(
                client,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "A${UUID.randomUUID().toString().take(8)}",
                        "name" to "Approval Test",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return UUID.fromString(json.readTree(response.body()).get("id").asString())
    }

    private fun account(client: HttpClient, csrf: String, company: UUID): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Test Reviewer',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        val response =
            command(
                client,
                "/api/v1/companies/$company/members/$id",
                json.writeValueAsString(
                    mapOf(
                        "permissions" to listOf("leave.approve", "approvals.read"),
                        "reason" to "Reviewer assignment",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    private fun template(
        client: HttpClient,
        csrf: String,
        company: UUID,
        id: UUID,
        assignees: Set<UUID>,
        version: Long? = null,
    ): HttpResponse<String> =
        command(
            client,
            "/api/v1/companies/$company/approvals/templates/$id",
            json.writeValueAsString(
                mapOf(
                    "name" to "Leave approval",
                    "kind" to "LEAVE",
                    "effectiveFrom" to "2026-01-01",
                    "expectedVersion" to version,
                    "stages" to listOf(mapOf("assignment" to "NAMED", "accountIds" to assignees)),
                    "reason" to "Approval policy setup",
                )
            ),
            csrf,
            UUID.randomUUID(),
            "PUT",
        )

    private fun admin(): UUID =
        database()
            .queryForObject(
                "select id from accounts where email='admin@example.test'",
                UUID::class.java,
            )!!

    private fun seed(company: UUID, template: UUID, assignees: Set<UUID>): ApprovalRequest {
        val actor = Actor(admin(), company, emptySet(), Instant.now(), UUID.randomUUID())
        val request =
            ApprovalRequest(
                UUID.randomUUID(),
                ApprovalKind.LEAVE,
                UUID.randomUUID(),
                actor.accountId,
                null,
                template,
                0,
                listOf(ApprovalStage(assignees)),
                0,
                if (assignees.isEmpty()) ApprovalStatus.BLOCKED else ApprovalStatus.PENDING,
                0,
                Instant.now(),
            )
        val result = transactions.run(actor) { approvals.create(company, request) }
        assertTrue(result is Result.Success, result.toString())
        return request
    }

    @Test
    fun submittedSnapshotsSurvivePolicyEditsAndDenyCrossCompanyReads() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val first = account(client, csrf, company)
        val second = account(client, csrf, company)
        val policy = UUID.randomUUID()
        val created = template(client, csrf, company, policy, setOf(first))
        assertEquals(200, created.statusCode(), created.body())
        val request = seed(company, policy, setOf(first))
        val updated = template(client, csrf, company, policy, setOf(second), 0)
        assertEquals(200, updated.statusCode(), updated.body())
        val list =
            get(client, "/api/v1/companies/$company/approvals/templates?kind=LEAVE&asOf=2026-10-08")
        assertEquals(200, list.statusCode(), list.body())
        assertEquals(1, json.readTree(list.body())[0].get("appliedRevision").asLong())
        val persisted = get(client, "/api/v1/companies/$company/approvals/${request.id}")
        assertEquals(0, json.readTree(persisted.body()).get("templateRevision").asLong())
        assertEquals(
            first.toString(),
            json.readTree(persisted.body()).get("stages")[0].get("assignees")[0].asString(),
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update approval_requests set stages='[]'::jsonb where company_id=? and id=?",
                    company,
                    request.id,
                )
        }
        val other = company(client, csrf)
        assertEquals(
            404,
            get(client, "/api/v1/companies/$other/approvals/${request.id}").statusCode(),
        )
    }

    @Test
    fun competingDecisionsCommitOneTransitionDecisionAndAudit() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val reviewers = setOf(account(client, csrf, company), account(client, csrf, company))
        val policy = UUID.randomUUID()
        val created = template(client, csrf, company, policy, reviewers)
        assertEquals(200, created.statusCode(), created.body())
        val request = seed(company, policy, reviewers)
        val read = CountDownLatch(2)
        val release = CountDownLatch(1)
        val outcomes =
            Executors.newFixedThreadPool(2).use { executor ->
                val tasks =
                    reviewers.map { id ->
                        executor.submit<Result<MutationReceipt>> {
                            val actor =
                                Actor(
                                    id,
                                    company,
                                    setOf("leave.approve"),
                                    Instant.now(),
                                    UUID.randomUUID(),
                                )
                            transactions.run(actor) {
                                val current =
                                    (approvals.find(company, request.id) as Result.Success).value!!
                                read.countDown()
                                check(release.await(15, TimeUnit.SECONDS))
                                decideApproval(
                                        current,
                                        actor,
                                        ApprovalDecision.APPROVE,
                                        "",
                                        emptyList(),
                                        emptyList(),
                                        Instant.now(),
                                    )
                                    .flatMap { transition ->
                                        approvals
                                            .decide(
                                                actor,
                                                request.id,
                                                current.version,
                                                current.currentStep,
                                                transition,
                                                "",
                                                Instant.now(),
                                            )
                                            .flatMap { receipt ->
                                                journal
                                                    .record(
                                                        actor,
                                                        ChangeRecord(
                                                            "approval_request",
                                                            request.id,
                                                            "test.approval_decided",
                                                        ),
                                                    )
                                                    .map { receipt }
                                            }
                                    }
                            }
                        }
                    }
                assertTrue(read.await(10, TimeUnit.SECONDS))
                release.countDown()
                tasks.map { it.get(20, TimeUnit.SECONDS) }
            }
        assertEquals(1, outcomes.count { it is Result.Success })
        assertEquals(
            "stale_version",
            (outcomes.filterIsInstance<Result.Failed>().single()).failure.code,
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from approval_decisions where company_id=? and request_id=?",
                    Int::class.java,
                    company,
                    request.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and resource_id=? and action='test.approval_decided'",
                    Int::class.java,
                    company,
                    request.id,
                ),
        )
        assertEquals(
            "APPROVED",
            database()
                .queryForObject(
                    "select status from approval_requests where company_id=? and id=?",
                    String::class.java,
                    company,
                    request.id,
                ),
        )
    }

    @Test
    fun blockedReassignmentIsVersionedAuditedAndPreservesOriginalSnapshot() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val reviewer = account(client, csrf, company)
        val policy = UUID.randomUUID()
        assertEquals(200, template(client, csrf, company, policy, setOf(reviewer)).statusCode())
        val request = seed(company, policy, emptySet())
        val operation = UUID.randomUUID()
        val body =
            json.writeValueAsString(
                mapOf(
                    "version" to 0,
                    "assignees" to setOf(reviewer),
                    "reason" to "Manager unavailable",
                )
            )
        val response =
            command(
                client,
                "/api/v1/companies/$company/approvals/${request.id}/reassign",
                body,
                csrf,
                operation,
            )
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(
            response.body(),
            command(
                    client,
                    "/api/v1/companies/$company/approvals/${request.id}/reassign",
                    body,
                    csrf,
                    operation,
                )
                .body(),
        )
        val effective = get(client, "/api/v1/companies/$company/approvals/${request.id}")
        assertEquals("PENDING", json.readTree(effective.body()).get("status").asString())
        assertEquals(
            reviewer.toString(),
            json.readTree(effective.body()).get("stages")[0].get("assignees")[0].asString(),
        )
        assertEquals(
            "[{\"assignees\": []}]",
            database()
                .queryForObject(
                    "select stages::text from approval_requests where company_id=? and id=?",
                    String::class.java,
                    company,
                    request.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from approval_assignment_overrides where company_id=? and request_id=?",
                    Int::class.java,
                    company,
                    request.id,
                ),
        )
        assertEquals(
            409,
            command(
                    client,
                    "/api/v1/companies/$company/approvals/${request.id}/reassign",
                    body,
                    csrf,
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
        val reviewerClient = client()
        login(reviewerClient, "$reviewer@example.test")
        val inbox = get(reviewerClient, "/api/v1/companies/$company/approvals")
        assertEquals(200, inbox.statusCode(), inbox.body())
        assertTrue(
            json.readTree(inbox.body()).get("items").any {
                it.get("id").asString() == request.id.toString()
            },
            inbox.body(),
        )
    }

    @Test
    fun delegationOwnershipIsImmutableAndRevocationStillWorksAfterTargetLosesAccess() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val first = account(client, csrf, company)
        val second = account(client, csrf, company)
        val fromClient = client()
        val fromCsrf = login(fromClient, "$first@example.test")
        val toClient = client()
        val toCsrf = login(toClient, "$second@example.test")
        val id = UUID.randomUUID()
        val from = Instant.now().minusSeconds(60).toString()
        val until = Instant.now().plusSeconds(3600).toString()
        val body =
            mapOf(
                "kind" to "LEAVE",
                "fromAccount" to first,
                "toAccount" to second,
                "validFrom" to from,
                "validUntil" to until,
                "reason" to "Scheduled absence",
            )
        val created =
            command(
                fromClient,
                "/api/v1/companies/$company/approvals/delegations/$id",
                json.writeValueAsString(body),
                fromCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, created.statusCode(), created.body())
        val takeover =
            command(
                toClient,
                "/api/v1/companies/$company/approvals/delegations/$id",
                json.writeValueAsString(
                    body +
                        mapOf("fromAccount" to second, "toAccount" to first, "expectedVersion" to 0)
                ),
                toCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(409, takeover.statusCode(), takeover.body())
        assertEquals("delegator_immutable", json.readTree(takeover.body()).get("code").asString())
        val disabled =
            command(
                client,
                "/api/v1/companies/$company/members/$second",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "active" to false,
                        "permissions" to listOf("leave.approve", "approvals.read"),
                        "reason" to "Access revoked",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, disabled.statusCode(), disabled.body())
        val revoked =
            command(
                fromClient,
                "/api/v1/companies/$company/approvals/delegations/$id",
                json.writeValueAsString(body + mapOf("active" to false, "expectedVersion" to 0)),
                fromCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, revoked.statusCode(), revoked.body())
        assertEquals(
            403,
            get(toClient, "/api/v1/companies/$company/approvals/delegations").statusCode(),
        )
    }

    @Test
    fun membershipAdministrationCannotGrantSensitiveAccessToItself() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val response =
            command(
                client,
                "/api/v1/companies/$company/members/${admin()}",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "permissions" to
                            listOf("identity.manage", "company.read", "payroll.finalize"),
                        "reason" to "Attempted self grant",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(403, response.statusCode(), response.body())
        assertEquals(
            "cannot_self_grant_sensitive_access",
            json.readTree(response.body()).get("code").asString(),
        )
    }
}
