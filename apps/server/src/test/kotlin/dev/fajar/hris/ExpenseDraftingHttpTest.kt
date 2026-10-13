package dev.fajar.hris

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseDraftingHttpTest : ExpenseReadAssuranceApiFixture() {
    @Test
    fun managementDiscoveryHasBoundedCompanyReferencesWithoutGrantingPeopleAccess() {
        val f = expenseFixture()
        val foreign = expenseFixture()
        val account = expenseMember(f.company, listOf("expenses.manage"))
        val operator = client()
        login(operator, "$account@example.test")
        val first = get(operator, "${path(f)}?limit=1")
        assertEquals(200, first.statusCode(), first.body())
        val body = json.readTree(first.body())
        assertEquals(
            setOf("companyDate", "timezone", "employees"),
            body.properties().map { it.key }.toSet(),
        )
        assertEquals(
            LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta")).toString(),
            body["companyDate"].asString(),
        )
        assertEquals("Asia/Jakarta", body["timezone"].asString())
        val row = body["employees"]["items"][0]
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            row.properties().map { it.key }.toSet(),
        )
        val next =
            get(operator, "${path(f)}?limit=1&after=${body["employees"]["nextCursor"].asString()}")
        assertEquals(200, next.statusCode(), next.body())
        val second = json.readTree(next.body())["employees"]
        assertTrue(second["nextCursor"].isNull)
        assertEquals(
            setOf(f.employee.toString(), f.manager.toString()),
            setOf(row["id"].asString(), second["items"][0]["id"].asString()),
        )
        val searched = get(operator, "${path(f)}?query=${row["employeeNumber"].asString()}")
        assertEquals(
            row["id"].asString(),
            json.readTree(searched.body())["employees"]["items"][0]["id"].asString(),
        )
        assertEquals(403, get(operator, path(foreign)).statusCode())
        assertEquals(403, get(f.worker, path(f)).statusCode())
        assertEquals(
            403,
            get(operator, "/api/v1/companies/${f.company}/employees?asOf=2026-10-01").statusCode(),
        )
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "query=${"x".repeat(121)}",
                "after=",
                "after=${"a".repeat(33)}",
            )) {
            val response = get(operator, "${path(f)}?$query")
            assertEquals(422, response.statusCode(), response.body())
            assertEquals("invalid_page", json.readTree(response.body())["code"].asString())
        }
    }

    @Test
    fun administrativeReferenceLookupDoesNotBroadenTheExistingSelfRoute() {
        val f = expenseFixture()
        val foreign = expenseFixture()
        val receipt = readyReceipt(f)
        val wrongEmployee = expenseReceipt(f, employee = f.manager)
        val account = expenseMember(f.company, listOf("expenses.manage"))
        val operator = client()
        login(operator, "$account@example.test")
        storageProbe.beforeRead = { error("Draft references must not read file contents") }
        val query =
            "categoryIds=${f.category},${foreign.category}&costCenterIds=${f.costCenter}&receiptRevisionIds=$receipt,$wrongEmployee"
        val response = get(operator, "${path(f)}/${f.employee}/references?$query")
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        val categories =
            body["categories"].iterator().asSequence().associateBy { it["id"].asString() }
        assertEquals(
            "Business travel",
            categories.getValue(f.category.toString())["name"].asString(),
        )
        assertTrue(categories.getValue(foreign.category.toString())["name"].isNull)
        val receipts =
            body["receipts"].iterator().asSequence().associateBy { it["revisionId"].asString() }
        assertEquals("READY", receipts.getValue(receipt.toString())["status"].asString())
        assertTrue(receipts.getValue(wrongEmployee.toString())["fileName"].isNull)
        assertFalse(response.body().contains("sha256") || response.body().contains("inspection"))
        assertEquals(
            404,
            get(
                    operator,
                    "/api/v1/companies/${f.company}/expenses/me/employments/${f.employee}/draft-references?$query",
                )
                .statusCode(),
        )
        assertEquals(403, get(f.worker, "${path(f)}/${f.employee}/references").statusCode())
        assertEquals(404, get(operator, "${path(f)}/${foreign.employee}/references").statusCode())
        assertEquals(
            403,
            get(operator, "/api/v1/companies/${f.company}/documents?employmentId=${f.employee}")
                .statusCode(),
        )
        for ((name, maximum) in
            listOf("categoryIds" to 20, "costCenterIds" to 20, "receiptRevisionIds" to 60)) {
            val ids = List(maximum + 1) { UUID.randomUUID() }.joinToString(",")
            val rejected = get(operator, "${path(f)}/${f.employee}/references?$name=$ids")
            assertEquals(422, rejected.statusCode(), rejected.body())
            assertEquals(
                "invalid_expense_references",
                json.readTree(rejected.body())["code"].asString(),
            )
        }
    }

    @Test
    fun receiptPickerReturnsOnlyPublishedReceiptMetadataForTheSelectedEmployment() {
        val f = expenseFixture()
        val foreign = expenseFixture()
        val ready = setOf(readyReceipt(f), readyReceipt(f))
        expenseReceipt(f)
        readyReceipt(f.copy(employee = f.manager))
        readyReceipt(foreign)
        readyReceipt(f, classification = "PERSONAL")
        val account = expenseMember(f.company, listOf("expenses.manage"))
        val operator = client()
        login(operator, "$account@example.test")
        storageProbe.beforeRead = { error("Receipt discovery must not read file contents") }
        val first = get(operator, "${path(f)}/${f.employee}/receipts?limit=1")
        assertEquals(200, first.statusCode(), first.body())
        val body = json.readTree(first.body())
        assertEquals(1, body["items"].size())
        assertEquals(
            setOf(
                "documentId",
                "revisionId",
                "revisionNumber",
                "title",
                "fileName",
                "mediaType",
                "size",
                "validatedAt",
            ),
            body["items"][0].properties().map { it.key }.toSet(),
        )
        val second =
            get(
                operator,
                "${path(f)}/${f.employee}/receipts?limit=1&after=${body["nextCursor"].asString()}",
            )
        assertEquals(200, second.statusCode(), second.body())
        val next = json.readTree(second.body())
        assertTrue(next["nextCursor"].isNull)
        assertEquals(
            ready.map(UUID::toString).toSet(),
            setOf(
                body["items"][0]["revisionId"].asString(),
                next["items"][0]["revisionId"].asString(),
            ),
        )
        assertFalse(
            first.body().contains("sha256") ||
                first.body().contains("storageKey") ||
                first.body().contains("inspection")
        )
        assertEquals(403, get(f.worker, "${path(f)}/${f.employee}/receipts").statusCode())
        assertEquals(404, get(operator, "${path(f)}/${foreign.employee}/receipts").statusCode())
        for (limit in listOf(0, 51)) assertEquals(
            422,
            get(operator, "${path(f)}/${f.employee}/receipts?limit=$limit").statusCode(),
        )
    }

    @Test
    fun viewingOrPayingExpensesDoesNotPermitAdministrativeDraftDiscovery() {
        val f = expenseFixture()
        for (permission in
            listOf(
                "expenses.read",
                "expenses.pay",
                "expenses.team.read",
                "expenses.self.manage",
                "expenses.policy.manage",
            )) {
            val account = expenseMember(f.company, listOf(permission))
            val operator = client()
            login(operator, "$account@example.test")
            for (suffix in
                listOf("", "/${f.employee}/receipts", "/${f.employee}/references")) assertEquals(
                403,
                get(operator, path(f) + suffix).statusCode(),
                permission,
            )
        }
    }

    private fun path(f: ExpenseFixture) =
        "/api/v1/companies/${f.company}/expenses/drafting/employees"
}
