package dev.fajar.hris

import java.net.http.HttpClient
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveBalanceDirectoryHttpTest : LeaveAccountingApiFixture() {
    @Test
    fun directoryPagesExistingAccountsWithCurrentLabelsAndNoAdministrativeHistory() {
        val f = leaveFixture()
        val empty = directory(f)
        assertEquals(f.employee.toString(), empty["employee"]["id"].asString())
        assertEquals("Example Employee", empty["employee"]["name"].asString())
        assertEquals(0, empty["balances"]["items"].size())
        accountingBody(adjust(f, "2.5"))
        accountingBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/leave/types/${f.type}",
                """{"code":"ANNUAL","name":"Revised annual leave","effectiveFrom":"2027-01-01","paid":true,"active":false,"expectedVersion":0,"reason":"Future policy label"}""",
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val second = f.copy(type = UUID.randomUUID())
        accountingBody(policy(second, code = "B-TYPE"))
        accountingBody(adjust(second, "1"))
        accountingBody(adjust(second, "-1"))

        val first = directory(f, suffix = "&limit=1")
        val row = first["balances"]["items"][0]
        assertEquals("ANNUAL", row["typeCode"].asString())
        assertEquals("Revised annual leave", row["typeName"].asString())
        assertEquals("Revised annual leave", accountingBody(ledger(f))["typeName"].asString())
        assertEquals("2.5", row["balance"]["availableDays"].asString())
        assertFalse(row["balance"]["accountId"].isNull)
        assertEquals(
            setOf("typeId", "typeCode", "typeName", "balance"),
            row.properties().map { it.key }.toSet(),
        )
        assertEquals("ANNUAL", first["balances"]["nextCursor"].asString())
        val next = directory(f, suffix = "&limit=1&after=ANNUAL")
        assertEquals("B-TYPE", next["balances"]["items"][0]["typeCode"].asString())
        assertEquals("0", next["balances"]["items"][0]["balance"]["availableDays"].asString())
        assertTrue(next["balances"]["nextCursor"].isNull)
        assertEquals(0, directory(f, year = 2027)["balances"]["items"].size())
        assertEquals(
            0,
            directory(f, employee = f.manager, client = f.admin)["balances"]["items"].size(),
        )
    }

    @Test
    fun directoryUsesCurrentSelfTeamAndCompanyScopeBeforeReturningEmployeeMetadata() {
        val f = leaveFixture()
        accountingBody(adjust(f, "3"))
        assertEquals(1, directory(f, client = f.supervisor)["balances"]["items"].size())
        val base = "/api/v1/companies/${f.company}/leave/employees"
        accountingCode(
            get(f.worker, "$base/${f.manager}/balances?year=2026"),
            404,
            "employee_not_found",
        )
        accountingCode(
            get(f.admin, "$base/${UUID.randomUUID()}/balances?year=2026"),
            404,
            "employee_not_found",
        )
        val other = leaveFixture()
        accountingCode(
            get(
                other.admin,
                "/api/v1/companies/${other.company}/leave/employees/${f.employee}/balances?year=2026",
            ),
            404,
            "employee_not_found",
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.team.read'",
                f.company,
                f.managerAccount,
            )
        accountingCode(
            get(f.supervisor, "$base/${f.employee}/balances?year=2026"),
            404,
            "employee_not_found",
        )
        for (query in
            listOf(
                "year=1899",
                "year=2201",
                "year=2026&limit=0",
                "year=2026&limit=201",
                "year=2026&after=bad",
                "year=2026&after=${"A".repeat(33)}",
            )) {
            accountingCode(
                get(f.worker, "$base/${f.employee}/balances?$query"),
                422,
                "invalid_page",
            )
        }
    }

    @Test
    fun closedAccountsAndUnfundedLedgerDetailsKeepExplicitIdentityAndYear() {
        val f = accountingFixture(at = java.time.Instant.parse("2027-01-02T03:00:00Z"))
        val zero = accountingBody(ledger(f))
        assertEquals(f.employee.toString(), zero["employee"]["id"].asString())
        assertEquals(f.type.toString(), zero["typeId"].asString())
        assertEquals("ANNUAL", zero["typeCode"].asString())
        assertEquals("Annual leave", zero["typeName"].asString())
        assertEquals("0", zero["balance"]["availableDays"].asString())
        assertTrue(zero["balance"]["accountId"].isNull)
        accountingBody(closeYear(f, body = closingBody(sourceVersion = 0)))
        val row = directory(f)["balances"]["items"][0]
        assertTrue(row["balance"]["closed"].asBoolean())
        assertEquals(2026, row["balance"]["year"].asInt())
        assertEquals(0, directory(f, year = 2027)["balances"]["items"].size())
        assertEquals(0, accountingRows(f, "leave_ledger"))
    }

    private fun directory(
        f: LeaveFixture,
        year: Int = 2026,
        suffix: String = "",
        employee: UUID = f.employee,
        client: HttpClient = f.worker,
    ) =
        accountingBody(
            get(
                client,
                "/api/v1/companies/${f.company}/leave/employees/$employee/balances?year=$year$suffix",
            )
        )
}
