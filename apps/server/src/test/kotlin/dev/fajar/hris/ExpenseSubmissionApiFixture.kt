package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.jobs.domain.entities.JobStep
import java.net.http.HttpClient
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import tools.jackson.databind.JsonNode

abstract class ExpenseSubmissionApiFixture : ExpenseApiFixture() {
    protected fun readyReceipt(
        f: ExpenseFixture,
        bytes: ByteArray = pdf + "\n%${UUID.randomUUID()}".toByteArray(),
    ): UUID {
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        val docs =
            Fixture(
                f.admin,
                f.adminCsrf,
                Actor(
                    admin,
                    f.company,
                    PermissionCatalog.companyAdministrator,
                    clock.instant(),
                    UUID.randomUUID(),
                ),
                f.employee,
            )
        val id = UUID.randomUUID()
        val started =
            start(docs, uploadInput(docs, bytes, revision = id, classification = "RECEIPT"))
        assertEquals(200, started.statusCode(), started.body())
        val uploaded = upload(docs, id, bytes)
        assertEquals(200, uploaded.statusCode(), uploaded.body())
        assertEquals(Result.Success(JobStep(1, true)), run(docs, beginValidation(docs, id)))
        assertEquals("READY", revision(docs, id).get("status").asString())
        return id
    }

    protected fun expenseTemplate(
        f: ExpenseFixture,
        id: UUID = UUID.randomUUID(),
        changes: Map<String, Any?> = emptyMap(),
    ): UUID {
        val response =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/approvals/templates/$id",
                json.writeValueAsString(
                    mapOf(
                        "name" to "Expense approval",
                        "kind" to "EXPENSE",
                        "effectiveFrom" to "2026-01-01",
                        "stages" to listOf(mapOf("assignment" to "MANAGER")),
                        "reason" to "Approval setup",
                    ) + changes
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    protected fun expensePolicy(
        f: ExpenseFixture,
        category: UUID = f.category,
        version: Long? = 0,
        changes: Map<String, Any?> = emptyMap(),
    ) {
        val response =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/expenses/categories/$category",
                json.writeValueAsString(
                    mapOf(
                        "code" to "TRAVEL",
                        "name" to "Business travel",
                        "effectiveFrom" to "2026-01-01",
                        "maximumLineAmount" to "500000.00",
                        "maximumClaimAmount" to "2000000.00",
                        "maximumAgeDays" to 30,
                        "reason" to "Policy revision",
                        "expectedVersion" to version,
                    ) + changes
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, response.statusCode(), response.body())
    }

    protected fun readyLines(f: ExpenseFixture, receipt: UUID = readyReceipt(f)) =
        listOf(expenseLine(f, mapOf("receiptRevisionIds" to listOf(receipt))))

    protected fun submitExpense(
        f: ExpenseFixture,
        id: UUID,
        version: Long = 0,
        key: UUID = UUID.randomUUID(),
        asAdmin: Boolean = false,
    ) =
        command(
            if (asAdmin) f.admin else f.worker,
            "${f.path}/${f.claim}/submit",
            json.writeValueAsString(
                mapOf(
                    "submissionId" to id,
                    "expectedVersion" to version,
                    "reason" to "Submit reimbursement",
                )
            ),
            if (asAdmin) f.adminCsrf else f.workerCsrf,
            key,
        )

    protected fun withdrawExpense(
        f: ExpenseFixture,
        id: UUID,
        version: Long,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.worker,
            "/api/v1/companies/${f.company}/expenses/submissions/$id/withdraw",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Correct expense details")
            ),
            f.workerCsrf,
            key,
        )

    protected fun submissionDetails(
        f: ExpenseFixture,
        id: UUID,
        browser: HttpClient = f.worker,
    ): JsonNode {
        val response = get(browser, "/api/v1/companies/${f.company}/expenses/submissions/$id")
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }
}
