package dev.fajar.hris

import java.net.http.HttpClient
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class LifecycleContextHttpTest : LifecycleApiFixture() {
    private fun namedEmployee(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        name: String,
    ): UUID {
        val id = UUID.randomUUID()
        val result =
            command(
                browser,
                "/api/v1/companies/$company/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "employeeNumber" to "E${id.toString().take(8)}",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "legalName" to name,
                                "nationality" to "ID",
                                "birthDate" to "1990-01-01",
                                "email" to "private-$id@example.test",
                            ),
                        "terms" to terms(),
                        "reason" to "Lifecycle context fixture",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, result.statusCode(), result.body())
        return id
    }

    private fun assertReference(body: JsonNode, employee: UUID, name: String) {
        assertEquals(
            json.readTree(
                json.writeValueAsString(
                    mapOf(
                        "id" to employee,
                        "employeeNumber" to "E${employee.toString().take(8).uppercase()}",
                        "name" to name,
                    )
                )
            ),
            body["employee"],
        )
        assertEquals(employee.toString(), body["employmentId"].asString())
        for (privateField in listOf("email", "birthDate", "nationality", "personId", "accountId")) {
            assertFalse(body.has(privateField))
        }
        assertFalse(body.toString().contains("private-"))
    }

    @Test
    fun lifecycleReadersReceiveCurrentEmployeeContextWithoutPrivateProfileAccess() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val employee = namedEmployee(admin, csrf, company, "Initial employee")
        val template = template(admin, csrf, company)
        val id = case(admin, csrf, company, employee, template)
        val readerId = user()
        member(company, readerId, setOf("people.lifecycle.read"))
        val reader = client()
        login(reader, "$readerId@example.test")
        val details = get(reader, "${api(company)}/cases/$id")
        assertEquals(200, details.statusCode(), details.body())
        assertReference(json.readTree(details.body()), employee, "Initial employee")
        val list = get(reader, "${api(company)}/cases?employmentId=$employee&status=OPEN&limit=1")
        assertEquals(200, list.statusCode(), list.body())
        assertReference(json.readTree(list.body())["items"][0], employee, "Initial employee")
        for ((path, code) in
            listOf(
                "/api/v1/companies/$company/employees/$employee?asOf=2026-10-01" to
                    "employee_not_found",
                "/api/v1/companies/$company/employees/$employee/profile" to
                    "person_profile_not_found",
            )) {
            val hidden = get(reader, path)
            assertEquals(404, hidden.statusCode(), hidden.body())
            assertEquals(code, json.readTree(hidden.body())["code"].asString())
            assertFalse(hidden.body().contains("Initial employee"))
        }
        val changed =
            command(
                admin,
                "/api/v1/companies/$company/employees/$employee/profile",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "legalName" to "Current employee",
                        "nationality" to "ID",
                        "birthDate" to "1990-01-01",
                        "email" to "private-$employee@example.test",
                        "reason" to "Corrected legal name",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, changed.statusCode(), changed.body())
        val current = json.readTree(get(reader, "${api(company)}/cases/$id").body())
        assertReference(current, employee, "Current employee")
        assertEquals(0, current["version"].asLong())
        assertEquals(0, current["templateVersion"].asLong())
        assertEquals("Employee checklist", current["templateName"].asString())
        assertReference(
            json.readTree(get(reader, "${api(company)}/cases").body())["items"][0],
            employee,
            "Current employee",
        )
    }

    @Test
    fun caseReferencesStayInsideTheSelectedCompanyAndEmployeeFilter() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val otherCompany = company(admin, csrf)
        val employee = namedEmployee(admin, csrf, company, "Selected employee")
        val otherEmployee = namedEmployee(admin, csrf, otherCompany, "Foreign employee")
        val id = case(admin, csrf, company, employee, template(admin, csrf, company))
        val foreign =
            case(admin, csrf, otherCompany, otherEmployee, template(admin, csrf, otherCompany))
        val readerId = user()
        member(company, readerId, setOf("people.lifecycle.read"))
        val reader = client()
        login(reader, "$readerId@example.test")
        val page = get(reader, "${api(company)}/cases")
        assertEquals(200, page.statusCode(), page.body())
        assertEquals(1, json.readTree(page.body())["items"].size())
        assertEquals(id.toString(), json.readTree(page.body())["items"][0]["id"].asString())
        assertReference(json.readTree(page.body())["items"][0], employee, "Selected employee")
        assertFalse(page.body().contains("Foreign employee"))
        val missing = get(reader, "${api(company)}/cases/$foreign")
        assertEquals(404, missing.statusCode(), missing.body())
        assertEquals("lifecycle_case_not_found", json.readTree(missing.body())["code"].asString())
        assertEquals(403, get(reader, "${api(otherCompany)}/cases/$foreign").statusCode())
        val filtered = get(reader, "${api(company)}/cases?employmentId=$otherEmployee")
        assertEquals(200, filtered.statusCode(), filtered.body())
        assertEquals(0, json.readTree(filtered.body())["items"].size())
    }

    @Test
    fun assigneesReceiveOnlyTheirOwnPendingTaskContextAndLoseItAfterReassignmentOrCompletion() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val assignee = user()
        val other = user()
        member(company, assignee, setOf("people.lifecycle.perform"))
        member(company, other, setOf("people.lifecycle.perform"))
        val employee = namedEmployee(admin, csrf, company, "Assigned employee")
        val hiddenEmployee = namedEmployee(admin, csrf, company, "Other employee")
        val template = template(admin, csrf, company)
        val id =
            case(
                admin,
                csrf,
                company,
                employee,
                template,
                assignees = mapOf("equipment" to assignee),
            )
        case(
            admin,
            csrf,
            company,
            hiddenEmployee,
            template,
            assignees = mapOf("equipment" to other),
        )
        val reader = client()
        login(reader, "$assignee@example.test")
        val queue = get(reader, "${api(company)}/tasks/assigned")
        assertEquals(200, queue.statusCode(), queue.body())
        assertEquals(1, json.readTree(queue.body())["items"].size())
        assertReference(json.readTree(queue.body())["items"][0], employee, "Assigned employee")
        assertFalse(queue.body().contains("Other employee"))
        assertEquals(403, get(reader, "${api(company)}/cases/$id").statusCode())
        val hiddenProfile = get(reader, "/api/v1/companies/$company/employees/$employee/profile")
        assertEquals(404, hiddenProfile.statusCode(), hiddenProfile.body())
        assertEquals(
            "person_profile_not_found",
            json.readTree(hiddenProfile.body())["code"].asString(),
        )
        assertFalse(hiddenProfile.body().contains("Assigned employee"))
        val assignment =
            command(
                admin,
                "${api(company)}/cases/$id/tasks/equipment/assignee",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "assigneeId" to other,
                        "reason" to "Changed task owner",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, assignment.statusCode(), assignment.body())
        val removed = get(reader, "${api(company)}/tasks/assigned")
        assertEquals(200, removed.statusCode(), removed.body())
        assertEquals(0, json.readTree(removed.body())["items"].size())
        assertFalse(removed.body().contains("Assigned employee"))
        val delegate = client()
        val delegateCsrf = login(delegate, "$other@example.test")
        assertEquals(
            2,
            json.readTree(get(delegate, "${api(company)}/tasks/assigned").body())["items"].size(),
        )
        assertEquals(200, change(delegate, delegateCsrf, company, id, 1).statusCode())
        val remaining = get(delegate, "${api(company)}/tasks/assigned")
        assertEquals(200, remaining.statusCode(), remaining.body())
        assertEquals(1, json.readTree(remaining.body())["items"].size())
        assertReference(
            json.readTree(remaining.body())["items"][0],
            hiddenEmployee,
            "Other employee",
        )
        assertFalse(remaining.body().contains("Assigned employee"))
    }
}
