package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class PeopleHttpTest : ApiIntegrationTest() {
    private fun company(client: HttpClient, csrf: String): String {
        val body =
            json.writeValueAsString(
                mapOf(
                    "code" to "P${UUID.randomUUID().toString().take(8)}",
                    "name" to "People Test",
                    "timezone" to "Asia/Jakarta",
                )
            )
        val response = command(client, "/api/v1/companies", body, csrf, UUID.randomUUID())
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body()).get("id").asString()
    }

    private fun department(
        client: HttpClient,
        csrf: String,
        company: String,
        parent: UUID? = null,
    ): UUID {
        val id = UUID.randomUUID()
        val response =
            command(
                client,
                "/api/v1/companies/$company/organization-units/$id",
                json.writeValueAsString(
                    mapOf(
                        "code" to "D${id.toString().take(8)}",
                        "name" to "Operations",
                        "kind" to "DEPARTMENT",
                        "parentId" to parent,
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    private fun terms(
        department: UUID? = null,
        manager: UUID? = null,
        effectiveFrom: String = "2026-01-01",
    ): Map<String, Any?> =
        mapOf(
            "effectiveFrom" to effectiveFrom,
            "startDate" to "2026-01-01",
            "contract" to "PERMANENT",
            "status" to "ACTIVE",
            "departmentId" to department,
            "managerId" to manager,
        )

    private fun employeeBody(
        id: UUID,
        number: String,
        department: UUID? = null,
        manager: UUID? = null,
        account: UUID? = null,
    ): String =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "employeeNumber" to number,
                "person" to
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "accountId" to account,
                        "legalName" to "Example $number",
                        "birthDate" to "1990-01-01",
                        "nationality" to "ID",
                    ),
                "terms" to terms(department, manager),
                "reason" to "Administrative onboarding",
            )
        )

    @Test
    fun effectiveHistoryIsAppendOnlyAndCreateReplaySurvivesArchivedReferences() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val original = department(client, csrf, company)
        val replacement = department(client, csrf, company)
        val employee = UUID.randomUUID()
        val key = UUID.randomUUID()
        val body = employeeBody(employee, "EMP001", original)
        val created = command(client, "/api/v1/companies/$company/employees", body, csrf, key)
        assertEquals(200, created.statusCode(), created.body())
        val changed =
            command(
                client,
                "/api/v1/companies/$company/employees/$employee/revisions",
                json.writeValueAsString(
                    mapOf(
                        "version" to 0,
                        "terms" to terms(replacement, effectiveFrom = "2026-10-01"),
                        "reason" to "Department transfer",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, changed.statusCode(), changed.body())
        val before = get(client, "/api/v1/companies/$company/employees/$employee?asOf=2026-09-30")
        val after = get(client, "/api/v1/companies/$company/employees/$employee?asOf=2026-10-01")
        assertEquals(
            original.toString(),
            json.readTree(before.body()).get("terms").get("departmentId").asString(),
        )
        assertEquals(
            replacement.toString(),
            json.readTree(after.body()).get("terms").get("departmentId").asString(),
        )
        assertEquals(0, json.readTree(before.body()).get("appliedRevision").asLong())
        assertEquals(1, json.readTree(after.body()).get("appliedRevision").asLong())
        val archive =
            command(
                client,
                "/api/v1/companies/$company/organization-units/$original",
                json.writeValueAsString(
                    mapOf(
                        "code" to "D${original.toString().take(8)}",
                        "name" to "Operations",
                        "kind" to "DEPARTMENT",
                        "active" to false,
                        "expectedVersion" to 0,
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, archive.statusCode(), archive.body())
        assertEquals(
            created.body(),
            command(client, "/api/v1/companies/$company/employees", body, csrf, key).body(),
        )
        val stale =
            command(
                client,
                "/api/v1/companies/$company/employees/$employee/revisions",
                json.writeValueAsString(
                    mapOf(
                        "version" to 0,
                        "terms" to terms(replacement, effectiveFrom = "2026-10-01"),
                        "reason" to "Stale edit",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(409, stale.statusCode(), stale.body())
        val history = get(client, "/api/v1/companies/$company/employees/$employee/history?limit=1")
        assertEquals(1, json.readTree(history.body()).get("items").size())
        assertEquals("1", json.readTree(history.body()).get("nextCursor").asString())
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update employment_revisions set reason='overwrite' where employment_id=?",
                    employee,
                )
        }
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from employment_revisions where employment_id=?",
                    Int::class.java,
                    employee,
                ),
        )
    }

    @Test
    fun foreignCompanyAssignmentsAreRejectedBeforeAnyPersonIsInserted() {
        val client = client()
        val csrf = login(client)
        val first = company(client, csrf)
        val second = company(client, csrf)
        val foreign = department(client, csrf, second)
        val body = employeeBody(UUID.randomUUID(), "CROSS01", foreign)
        val operation = UUID.randomUUID()
        val response = command(client, "/api/v1/companies/$first/employees", body, csrf, operation)
        assertEquals(422, response.statusCode(), response.body())
        assertEquals(
            "organization_assignment_unavailable",
            json.readTree(response.body()).get("code").asString(),
        )
        val person = UUID.fromString(json.readTree(body).get("person").get("id").asString())
        assertEquals(
            0,
            database()
                .queryForObject("select count(*) from persons where id=?", Int::class.java, person),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    operation,
                ),
        )
    }

    @Test
    fun simultaneousEmployeeNumbersCannotLeaveAnOrphanPerson() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val start = CountDownLatch(1)
        val responses =
            Executors.newFixedThreadPool(2).use { executor ->
                val tasks =
                    (1..2).map {
                        executor.submit<HttpResponse<String>> {
                            val body = employeeBody(UUID.randomUUID(), "DUP001")
                            start.await()
                            command(
                                client,
                                "/api/v1/companies/$company/employees",
                                body,
                                csrf,
                                UUID.randomUUID(),
                            )
                        }
                    }
                start.countDown()
                tasks.map { it.get(20, TimeUnit.SECONDS) }
            }
        assertEquals(
            listOf(200, 409),
            responses.map { it.statusCode() }.sorted(),
            responses.toString(),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    UUID.fromString(company),
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from persons where owner_company_id=?",
                    Int::class.java,
                    UUID.fromString(company),
                ),
        )
    }

    @Test
    fun organizationReparentingCannotCreateACycle() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val parent = department(client, csrf, company)
        val child = department(client, csrf, company, parent)
        val response =
            command(
                client,
                "/api/v1/companies/$company/organization-units/$parent",
                json.writeValueAsString(
                    mapOf(
                        "code" to "D${parent.toString().take(8)}",
                        "name" to "Operations",
                        "kind" to "DEPARTMENT",
                        "parentId" to child,
                        "expectedVersion" to 0,
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(409, response.statusCode(), response.body())
        assertEquals(
            "organization_cycle_or_depth",
            json.readTree(response.body()).get("code").asString(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from organization_units where company_id=? and id=?",
                    Int::class.java,
                    UUID.fromString(company),
                    parent,
                ),
        )
    }

    @Test
    fun teamAndSelfPermissionsFilterBothDirectoriesAndDetailReads() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val managerAccount = UUID.randomUUID()
        val workerAccount = UUID.randomUUID()
        for ((account, permission) in
            listOf(managerAccount to "people.team.read", workerAccount to "people.self.read")) {
            database()
                .update(
                    "insert into accounts(id,email,display_name,password_hash) select ?,?,'Test Account',password_hash from accounts where email='admin@example.test'",
                    account,
                    "$account@example.test",
                )
            database()
                .update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    UUID.fromString(company),
                    account,
                )
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    UUID.fromString(company),
                    account,
                    permission,
                )
        }
        val manager = UUID.randomUUID()
        val worker = UUID.randomUUID()
        val unrelated = UUID.randomUUID()
        for (body in
            listOf(
                employeeBody(manager, "MANAGER", account = managerAccount),
                employeeBody(worker, "WORKER", manager = manager, account = workerAccount),
                employeeBody(unrelated, "UNRELATED"),
            )) {
            val result =
                command(
                    client,
                    "/api/v1/companies/$company/employees",
                    body,
                    csrf,
                    UUID.randomUUID(),
                )
            assertEquals(200, result.statusCode(), result.body())
        }
        for (account in listOf(managerAccount, workerAccount)) {
            val scoped = client()
            val token =
                json.readTree(get(scoped, "/api/v1/auth/csrf").body()).get("token").asString()
            assertEquals(
                200,
                post(
                        scoped,
                        "/api/v1/auth/login",
                        json.writeValueAsString(
                            mapOf(
                                "email" to "$account@example.test",
                                "password" to "Testing-password-123!",
                            )
                        ),
                        token,
                    )
                    .statusCode(),
            )
            val directory = get(scoped, "/api/v1/companies/$company/employees?asOf=2026-10-01")
            assertEquals(200, directory.statusCode(), directory.body())
            val items = json.readTree(directory.body()).get("items")
            assertEquals(1, items.size())
            assertEquals(worker.toString(), items[0].get("id").asString())
            assertFalse(directory.body().contains("birthDate"))
            assertEquals(
                404,
                get(scoped, "/api/v1/companies/$company/employees/$unrelated?asOf=2026-10-01")
                    .statusCode(),
            )
            assertEquals(
                403,
                get(scoped, "/api/v1/companies/$company/employees/$worker/history").statusCode(),
            )
        }
    }
}
