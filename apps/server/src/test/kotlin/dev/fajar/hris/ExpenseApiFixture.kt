package dev.fajar.hris

import java.net.http.HttpClient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import tools.jackson.databind.JsonNode

abstract class ExpenseApiFixture : PeopleApiFixture() {
    protected data class ExpenseFixture(
        val company: UUID,
        val category: UUID,
        val costCenter: UUID,
        val claim: UUID,
        val line: UUID,
        val employee: UUID,
        val account: UUID,
        val manager: UUID,
        val managerAccount: UUID,
        val admin: HttpClient,
        val adminCsrf: String,
        val worker: HttpClient,
        val workerCsrf: String,
        val supervisor: HttpClient,
    ) {
        val path
            get() = "/api/v1/companies/$company/expenses/claims"
    }

    protected fun expenseFixture(): ExpenseFixture {
        clock.set(Instant.now())
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val account = expenseMember(company, listOf("company.read", "expenses.self.manage"))
        val managerAccount =
            expenseMember(
                company,
                listOf(
                    "company.read",
                    "expenses.team.read",
                    "expenses.team.approve",
                    "approvals.read",
                ),
            )
        val manager = employee(admin, csrf, company, account = managerAccount)
        val employee = employee(admin, csrf, company, manager = manager, account = account)
        val category = UUID.randomUUID()
        val policy =
            command(
                admin,
                "/api/v1/companies/$company/expenses/categories/$category",
                json.writeValueAsString(
                    mapOf(
                        "code" to "TRAVEL",
                        "name" to "Business travel",
                        "effectiveFrom" to "2026-01-01",
                        "maximumLineAmount" to "500000.00",
                        "maximumClaimAmount" to "2000000.00",
                        "maximumAgeDays" to 30,
                        "reason" to "Expense policy configuration",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, policy.statusCode(), policy.body())
        val costCenter = expenseUnit(admin, csrf, company)
        val worker = client()
        val workerCsrf = login(worker, "$account@example.test")
        val supervisor = client()
        login(supervisor, "$managerAccount@example.test")
        return ExpenseFixture(
            company,
            category,
            costCenter,
            UUID.randomUUID(),
            UUID.randomUUID(),
            employee,
            account,
            manager,
            managerAccount,
            admin,
            csrf,
            worker,
            workerCsrf,
            supervisor,
        )
    }

    protected fun expenseMember(company: UUID, permissions: List<String>): UUID {
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Expense member',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    account,
                    it,
                )
        }
        return account
    }

    protected fun expenseUnit(
        admin: HttpClient,
        csrf: String,
        company: UUID,
        kind: String = "COST_CENTER",
    ): UUID {
        val id = UUID.randomUUID()
        val result =
            command(
                admin,
                "/api/v1/companies/$company/organization-units/$id",
                json.writeValueAsString(
                    mapOf(
                        "code" to "CC${id.toString().take(8)}",
                        "name" to "Operations",
                        "kind" to kind,
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, result.statusCode(), result.body())
        return id
    }

    protected fun expenseLine(
        f: ExpenseFixture,
        changes: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        mapOf(
            "id" to f.line,
            "categoryId" to f.category,
            "occurredOn" to
                LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta")).toString(),
            "amount" to "150000.00",
            "description" to "Client visit",
            "costCenterId" to f.costCenter,
            "receiptRevisionIds" to emptyList<UUID>(),
        ) + changes

    protected fun expenseBody(
        f: ExpenseFixture,
        version: Long? = null,
        lines: List<Map<String, Any?>> = listOf(expenseLine(f)),
        changes: Map<String, Any?> = emptyMap(),
    ): String =
        json.writeValueAsString(
            mapOf(
                "employmentId" to f.employee,
                "expectedVersion" to version,
                "title" to "Travel reimbursement",
                "description" to "Client visit",
                "lines" to lines,
                "reason" to "Expense draft saved",
            ) + changes
        )

    protected fun saveExpense(
        f: ExpenseFixture,
        version: Long? = null,
        lines: List<Map<String, Any?>> = listOf(expenseLine(f)),
        changes: Map<String, Any?> = emptyMap(),
        key: UUID = UUID.randomUUID(),
        asAdmin: Boolean = false,
    ) =
        command(
            if (asAdmin) f.admin else f.worker,
            "${f.path}/${f.claim}/draft",
            expenseBody(f, version, lines, changes),
            if (asAdmin) f.adminCsrf else f.workerCsrf,
            key,
            "PUT",
        )

    protected fun cancelExpense(f: ExpenseFixture, version: Long, key: UUID = UUID.randomUUID()) =
        command(
            f.worker,
            "${f.path}/${f.claim}/cancel",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Expense no longer required")
            ),
            f.workerCsrf,
            key,
        )

    protected fun expenseDetails(f: ExpenseFixture, browser: HttpClient = f.worker): JsonNode {
        val result = get(browser, "${f.path}/${f.claim}")
        assertEquals(200, result.statusCode(), result.body())
        return json.readTree(result.body())
    }

    protected fun expenseReceipt(
        f: ExpenseFixture,
        company: UUID = f.company,
        employee: UUID = f.employee,
        classification: String = "RECEIPT",
    ): UUID {
        val revision = UUID.randomUUID()
        val result =
            command(
                f.admin,
                "/api/v1/companies/$company/documents/uploads",
                json.writeValueAsString(
                    mapOf(
                        "documentId" to UUID.randomUUID(),
                        "revisionId" to revision,
                        "employmentId" to employee,
                        "title" to "Expense receipt",
                        "classification" to classification,
                        "expectedDocumentVersion" to 0,
                        "fileName" to "receipt.pdf",
                        "mediaType" to "application/pdf",
                        "size" to 3,
                        "sha256" to "a".repeat(64),
                        "reason" to "Expense evidence",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, result.statusCode(), result.body())
        return revision
    }

    protected fun expensePeriod(): String {
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        return "from=$today&until=$today"
    }
}
