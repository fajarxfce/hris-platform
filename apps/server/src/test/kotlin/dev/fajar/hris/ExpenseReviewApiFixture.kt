package dev.fajar.hris

import java.net.http.HttpClient
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class ExpenseReviewApiFixture : ExpenseSubmissionApiFixture() {
    protected data class Reviewer(val account: UUID, val browser: HttpClient, val csrf: String)

    protected fun reviewer(f: ExpenseFixture, account: UUID = f.managerAccount): Reviewer {
        val browser = client()
        val admin = adminAccount()
        return Reviewer(
            account,
            browser,
            login(browser, if (account == admin) "admin@example.test" else "$account@example.test"),
        )
    }

    protected fun adminAccount() =
        database()
            .queryForObject(
                "select id from accounts where email='admin@example.test'",
                UUID::class.java,
            )!!

    protected fun pendingExpense(
        f: ExpenseFixture,
        lines: List<Map<String, Any?>> = readyLines(f),
        asAdmin: Boolean = false,
    ): UUID {
        val saved = saveExpense(f, lines = lines, asAdmin = asAdmin)
        assertEquals(200, saved.statusCode(), saved.body())
        val id = UUID.randomUUID()
        val submitted = submitExpense(f, id, asAdmin = asAdmin)
        assertEquals(200, submitted.statusCode(), submitted.body())
        return id
    }

    protected fun reviewExpense(
        f: ExpenseFixture,
        id: UUID,
        reviewer: Reviewer,
        version: Long = 1,
        approvalVersion: Long = 0,
        decision: String = "APPROVE",
        reason: String = "",
        acknowledge: Boolean = false,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            reviewer.browser,
            "/api/v1/companies/${f.company}/expenses/submissions/$id/decisions",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "expectedApprovalVersion" to approvalVersion,
                    "decision" to decision,
                    "reason" to reason,
                    "acknowledgeDuplicates" to acknowledge,
                )
            ),
            reviewer.csrf,
            key,
        )

    protected fun reassignExpense(
        f: ExpenseFixture,
        id: UUID,
        assignees: Set<UUID>,
        version: Long = 0,
    ) =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/approvals/$id/reassign",
            json.writeValueAsString(
                mapOf(
                    "version" to version,
                    "assignees" to assignees,
                    "reason" to "Reviewer assignment changed",
                )
            ),
            f.adminCsrf,
            UUID.randomUUID(),
        )

    protected fun expenseDelegation(
        f: ExpenseFixture,
        from: Reviewer,
        to: UUID,
        id: UUID = UUID.randomUUID(),
        version: Long? = null,
        active: Boolean = true,
        until: Instant = clock.instant().plusSeconds(3600),
    ): UUID {
        val result =
            command(
                from.browser,
                "/api/v1/companies/${f.company}/approvals/delegations/$id",
                json.writeValueAsString(
                    mapOf(
                        "kind" to "EXPENSE",
                        "fromAccount" to from.account,
                        "toAccount" to to,
                        "validFrom" to clock.instant().minusSeconds(60).toString(),
                        "validUntil" to until.toString(),
                        "expectedVersion" to version,
                        "active" to active,
                        "reason" to "Reviewer availability",
                    )
                ),
                from.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, result.statusCode(), result.body())
        return id
    }
}
