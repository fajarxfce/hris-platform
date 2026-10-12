package dev.fajar.hris

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentEmployeesHttpTest : DocumentEmployeesApiFixture() {
    @Test
    fun directoryUsesDocumentGrantsAndReturnsOnlyScopedDisplayReferences() {
        val (administrator, reader) = directoryFixture()
        val foreignCompany = company(administrator.browser, administrator.csrf)
        val foreignEmployee = employee(administrator.browser, administrator.csrf, foreignCompany)
        database()
            .update("update companies set timezone='Pacific/Honolulu' where id=?", reader.company)
        val response = get(reader.browser, "${reader.path}/employees?limit=1")
        assertEquals(200, response.statusCode(), response.body())
        val directory = json.readTree(response.body())
        assertEquals(reader.company.toString(), directory["companyId"].asString())
        assertEquals("Pacific/Honolulu", directory["timezone"].asString())
        assertEquals(
            LocalDate.ofInstant(clock.instant(), ZoneId.of("Pacific/Honolulu")).toString(),
            directory["asOf"].asString(),
        )
        assertEquals("", directory["query"].asString())
        val page = directory["employees"]
        val first = page["items"].single()
        assertEquals(
            setOf("id", "employeeNumber", "name", "status", "version"),
            first.properties().map { it.key }.toSet(),
        )
        assertEquals("Example employee", first["name"].asString())
        assertFalse(response.body().contains(foreignEmployee.toString()))
        assertEquals(0, storageProbe.reads.get())
        assertFalse(page["nextCursor"].isNull)
        val next =
            get(
                reader.browser,
                "${reader.path}/employees?after=${page["nextCursor"].asString()}&limit=1",
            )
        assertEquals(200, next.statusCode(), next.body())
        val remaining = json.readTree(next.body())["employees"]
        assertEquals(1, remaining["items"].size())
        assertNotEquals(first["id"].asString(), remaining["items"][0]["id"].asString())
        assertTrue(remaining["nextCursor"].isNull)
        failure(
            get(reader.browser, "/api/v1/companies/$foreignCompany/documents/employees"),
            403,
            "company_access_denied",
        )
        // The document projection does not grant the broader People directory.
        assertEquals(
            403,
            get(reader.browser, "/api/v1/companies/${reader.company}/employees?asOf=2026-10-01")
                .statusCode(),
        )
    }

    @Test
    fun searchIsLiteralBoundedAndRequiresBothAdministrativeReadGrants() {
        val (_, reader) = directoryFixture()
        val response = get(reader.browser, "${reader.path}/employees?query=%20example%20")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("example", json.readTree(response.body())["query"].asString())
        assertEquals(2, json.readTree(response.body())["employees"]["items"].size())
        val number =
            json.readTree(response.body())["employees"]["items"][0]["employeeNumber"].asString()
        val byNumber = get(reader.browser, "${reader.path}/employees?query=$number")
        assertEquals(200, byNumber.statusCode(), byNumber.body())
        assertEquals(1, json.readTree(byNumber.body())["employees"]["items"].size())
        for (query in listOf("%", "_", "' OR 1=1 --")) {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
            val literal = get(reader.browser, "${reader.path}/employees?query=$encoded")
            assertEquals(200, literal.statusCode(), literal.body())
            assertEquals(0, json.readTree(literal.body())["employees"]["items"].size())
        }
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "query=${"A".repeat(121)}",
                "query=%0A",
                "after=",
                "after=${"A".repeat(33)}",
                "after=%25",
            )) failure(get(reader.browser, "${reader.path}/employees?$query"), 422, "invalid_page")
        for (permission in
            listOf("documents.self.read", "documents.read", "people.profile.read", "people.read")) {
            val account = selfFixture(permission).owner
            failure(get(account.browser, "${account.path}/employees"), 403, "access_denied")
        }
    }

    @Test
    fun exactReferencesSupportDirectNavigationWithoutLeakingForeignEmployment() {
        val (administrator, reader) = directoryFixture()
        val selected =
            get(reader.browser, "${reader.path}/employees?employmentId=${reader.employee}")
        assertEquals(200, selected.statusCode(), selected.body())
        val page = json.readTree(selected.body())["employees"]
        assertEquals(reader.employee.toString(), page["items"].single()["id"].asString())
        assertTrue(page["nextCursor"].isNull)
        val foreignCompany = company(administrator.browser, administrator.csrf)
        val foreign = employee(administrator.browser, administrator.csrf, foreignCompany)
        for (id in listOf(foreign, UUID.randomUUID())) {
            val missing = get(reader.browser, "${reader.path}/employees?employmentId=$id")
            assertEquals(200, missing.statusCode(), missing.body())
            assertEquals(0, json.readTree(missing.body())["employees"]["items"].size())
        }
        for (query in listOf("query=example", "after=EMPLOYEE-01")) failure(
            get(reader.browser, "${reader.path}/employees?employmentId=${reader.employee}&$query"),
            422,
            "invalid_page",
        )
    }
}
