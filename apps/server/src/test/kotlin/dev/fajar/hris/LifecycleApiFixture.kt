package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class LifecycleApiFixture : PeopleApiFixture() {
    protected fun api(company: UUID) = "/api/v1/companies/$company/lifecycle"

    protected fun tasks(optional: Boolean = false) =
        listOf(
            mapOf(
                "key" to "equipment",
                "title" to "Review equipment",
                "required" to true,
                "dueDays" to 0,
            )
        ) +
            if (optional)
                listOf(
                    mapOf(
                        "key" to "welcome",
                        "title" to "Welcome session",
                        "required" to false,
                        "dueDays" to 1,
                    )
                )
            else emptyList()

    protected fun saveTemplate(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        id: UUID,
        kind: String = "ONBOARDING",
        version: Long? = null,
        optional: Boolean = false,
        active: Boolean = true,
    ) =
        command(
            browser,
            "${api(company)}/templates/$id",
            json.writeValueAsString(
                mapOf(
                    "code" to "C${id.toString().take(8)}",
                    "name" to "Employee checklist",
                    "kind" to kind,
                    "active" to active,
                    "expectedVersion" to version,
                    "tasks" to tasks(optional),
                    "reason" to "Checklist policy",
                )
            ),
            csrf,
            UUID.randomUUID(),
            "PUT",
        )

    protected fun template(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        kind: String = "ONBOARDING",
        optional: Boolean = false,
    ): UUID {
        val id = UUID.randomUUID()
        val result = saveTemplate(browser, csrf, company, id, kind, optional = optional)
        assertEquals(200, result.statusCode(), result.body())
        return id
    }

    protected fun start(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        employee: UUID,
        template: UUID,
        id: UUID = UUID.randomUUID(),
        date: String = "2026-10-01",
        assignees: Map<String, UUID> = emptyMap(),
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            browser,
            "${api(company)}/cases",
            json.writeValueAsString(
                mapOf(
                    "id" to id,
                    "employmentId" to employee,
                    "templateId" to template,
                    "templateVersion" to 0,
                    "targetDate" to date,
                    "assignees" to assignees,
                    "reason" to "Employee transition",
                )
            ),
            csrf,
            key,
        )

    protected fun case(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        employee: UUID,
        template: UUID,
        date: String = "2026-10-01",
        assignees: Map<String, UUID> = emptyMap(),
    ): UUID {
        val id = UUID.randomUUID()
        val result = start(browser, csrf, company, employee, template, id, date, assignees)
        assertEquals(200, result.statusCode(), result.body())
        return id
    }

    protected fun change(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        id: UUID,
        version: Long,
        status: String = "DONE",
        task: String = "equipment",
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            browser,
            "${api(company)}/cases/$id/tasks/$task",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "status" to status, "reason" to "Task reviewed")
            ),
            csrf,
            key,
            "PUT",
        )

    protected fun finish(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        id: UUID,
        version: Long,
        offboarding: Boolean = false,
        employmentVersion: Long = 0,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            browser,
            "${api(company)}/cases/$id/complete-${if(offboarding) "offboarding" else "onboarding"}",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Transition verified") +
                    (if (offboarding) mapOf("employmentVersion" to employmentVersion)
                    else emptyMap())
            ),
            csrf,
            key,
        )

    protected fun cancel(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        id: UUID,
        version: Long,
    ) =
        command(
            browser,
            "${api(company)}/cases/$id/cancel",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Transition withdrawn")
            ),
            csrf,
            UUID.randomUUID(),
        )

    protected fun user(): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Lifecycle fixture',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        return id
    }

    protected fun member(
        company: UUID,
        account: UUID,
        permissions: Set<String> =
            setOf("company.read", "people.self.read", "people.lifecycle.perform"),
    ) {
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
    }

    protected fun transfer(
        browser: HttpClient,
        csrf: String,
        source: UUID,
        employee: UUID,
        target: UUID,
    ): HttpResponse<String> {
        val id = UUID.randomUUID()
        return command(
            browser,
            "/api/v1/companies/$source/employees/$employee/transfer",
            json.writeValueAsString(
                mapOf(
                    "targetCompanyId" to target,
                    "targetEmploymentId" to id,
                    "expectedVersion" to 0,
                    "employeeNumber" to "T${id.toString().take(8)}",
                    "terms" to terms("2026-10-01", start = "2026-10-01"),
                    "reason" to "Company transfer",
                )
            ),
            csrf,
            UUID.randomUUID(),
        )
    }
}
