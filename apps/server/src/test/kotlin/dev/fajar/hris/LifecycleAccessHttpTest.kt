package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class LifecycleAccessHttpTest : LifecycleApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe

    private data class Fixture(
        val account: UUID,
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val employee: UUID,
        val template: UUID,
        val case: UUID,
    )

    private fun fixture(assigned: Boolean = false): Fixture {
        val account = user()
        database()
            .update(
                "insert into platform_permissions(account_id,permission) select ?,permission from platform_permissions where account_id=(select id from accounts where email='admin@example.test')",
                account,
            )
        val browser = client()
        val csrf = login(browser, "$account@example.test")
        val company = company(browser, csrf)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.lifecycle.perform') on conflict do nothing",
                company,
                account,
            )
        val employee = employee(browser, csrf, company)
        val template = template(browser, csrf, company, optional = true)
        val case =
            case(
                browser,
                csrf,
                company,
                employee,
                template,
                assignees =
                    if (assigned) mapOf("equipment" to account, "welcome" to account)
                    else emptyMap(),
            )
        return Fixture(account, browser, csrf, company, employee, template, case)
    }

    private fun waiting(
        account: UUID,
        change: () -> Unit,
        request: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { request() }
            try {
                assertTrue(
                    barrier.entered.await(5, TimeUnit.SECONDS),
                    "The request must guard live account access",
                )
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    private fun remove(f: Fixture, permission: String) {
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                f.company,
                f.account,
                permission,
            )
    }

    @Test
    fun managementCommandsRecheckAuthorityBeforeChangesAndOriginalReceipts() {
        for (mode in listOf("template", "start", "assign", "cancel", "complete")) {
            val f = fixture()
            val base = api(f.company)
            val newEmployee =
                if (mode == "start") employee(f.browser, f.csrf, f.company) else f.employee
            if (mode == "complete") {
                assertEquals(200, change(f.browser, f.csrf, f.company, f.case, 0).statusCode())
                assertEquals(
                    200,
                    change(f.browser, f.csrf, f.company, f.case, 1, "WAIVED", "welcome")
                        .statusCode(),
                )
            }
            val payload: Map<String, Any?> =
                when (mode) {
                    "template" ->
                        mapOf(
                            "code" to "C${f.template.toString().take(8)}",
                            "name" to "Updated checklist",
                            "kind" to "ONBOARDING",
                            "active" to true,
                            "expectedVersion" to 0,
                            "tasks" to tasks(optional = true),
                            "reason" to "Revised policy",
                        )
                    "start" ->
                        mapOf(
                            "id" to UUID.randomUUID(),
                            "employmentId" to newEmployee,
                            "templateId" to f.template,
                            "templateVersion" to 0,
                            "targetDate" to "2026-10-01",
                            "assignees" to emptyMap<String, UUID>(),
                            "reason" to "Employee transition",
                        )
                    "assign" ->
                        mapOf(
                            "expectedVersion" to 0,
                            "assigneeId" to f.account,
                            "reason" to "Assign task",
                        )
                    "complete" -> mapOf("expectedVersion" to 2, "reason" to "Transition verified")
                    else -> mapOf("expectedVersion" to 0, "reason" to "Transition withdrawn")
                }
            val path =
                when (mode) {
                    "template" -> "$base/templates/${f.template}"
                    "start" -> "$base/cases"
                    "assign" -> "$base/cases/${f.case}/tasks/equipment/assignee"
                    "complete" -> "$base/cases/${f.case}/complete-onboarding"
                    else -> "$base/cases/${f.case}/cancel"
                }
            val body = json.writeValueAsString(payload)
            val key = UUID.randomUUID()
            val method = if (mode in setOf("template", "assign")) "PUT" else "POST"
            val request = { command(f.browser, path, body, f.csrf, key, method) }
            val denied = waiting(f.account, { remove(f, "people.lifecycle.manage") }, request)
            assertEquals(403, denied.statusCode(), "$mode ${denied.body()}")
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.lifecycle.manage')",
                    f.company,
                    f.account,
                )
            val saved = request()
            assertEquals(200, saved.statusCode(), "$mode ${saved.body()}")
            val replay =
                waiting(
                    f.account,
                    {
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    },
                    request,
                )
            assertEquals(401, replay.statusCode(), "$mode ${replay.body()}")
        }
    }

    @Test
    fun losingManagementAuthorityRetainsOnlyAssignedTaskActions() {
        for (assigned in listOf(false, true)) {
            val f = fixture(assigned)
            val result =
                waiting(f.account, { remove(f, "people.lifecycle.manage") }) {
                    change(f.browser, f.csrf, f.company, f.case, 0)
                }
            assertEquals(if (assigned) 200 else 403, result.statusCode(), result.body())
            if (!assigned)
                assertEquals(
                    "lifecycle_task_not_assigned",
                    json.readTree(result.body())["code"].asString(),
                )
            else {
                val denied = change(f.browser, f.csrf, f.company, f.case, 1, "WAIVED", "welcome")
                assertEquals(422, denied.statusCode(), denied.body())
            }
        }
    }

    @Test
    fun newlyGrantedManagementDoesNotExpandAnAlreadyResolvedTaskRequest() {
        val f = fixture(assigned = true)
        remove(f, "people.lifecycle.manage")
        val result =
            waiting(
                f.account,
                {
                    database()
                        .update(
                            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.lifecycle.manage')",
                            f.company,
                            f.account,
                        )
                },
            ) {
                change(f.browser, f.csrf, f.company, f.case, 0, "WAIVED", "welcome")
            }
        assertEquals(422, result.statusCode(), result.body())
        assertEquals(
            "lifecycle_task_cannot_be_waived",
            json.readTree(result.body())["code"].asString(),
        )
        assertEquals(
            200,
            change(f.browser, f.csrf, f.company, f.case, 0, "WAIVED", "welcome").statusCode(),
        )
    }

    @Test
    fun everyReadRechecksItsRemainingScopeAfterWaiting() {
        for (mode in
            listOf(
                "templates",
                "cases",
                "detail",
                "history",
                "assigned",
                "assignees",
                "credentials",
                "membership",
            )) {
            val f = fixture(assigned = true)
            val path =
                api(f.company) +
                    when (mode) {
                        "templates" -> "/templates"
                        "cases" -> "/cases?limit=1"
                        "history" -> "/cases/${f.case}/history"
                        "assigned" -> "/tasks/assigned"
                        "assignees" -> "/assignees"
                        else -> "/cases/${f.case}"
                    }
            val result =
                waiting(
                    f.account,
                    {
                        when (mode) {
                            "credentials" ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.account,
                                    )
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                                        f.company,
                                        f.account,
                                    )
                            "assignees" -> remove(f, "people.lifecycle.manage")
                            "assigned" -> {
                                remove(f, "people.lifecycle.perform")
                                remove(f, "people.lifecycle.manage")
                            }
                            else -> remove(f, "people.lifecycle.read")
                        }
                    },
                ) {
                    get(f.browser, path)
                }
            assertEquals(
                if (mode == "credentials") 401 else 403,
                result.statusCode(),
                "$mode ${result.body()}",
            )
        }
    }

    @Test
    fun taskReceiptCannotBypassRevokedTaskAccess() {
        val f = fixture(assigned = true)
        remove(f, "people.lifecycle.manage")
        val key = UUID.randomUUID()
        val request = { change(f.browser, f.csrf, f.company, f.case, 0, key = key) }
        assertEquals(200, request().statusCode())
        val result = waiting(f.account, { remove(f, "people.lifecycle.perform") }, request)
        assertEquals(403, result.statusCode(), result.body())
    }

    @Test
    fun assigningAndStartingCasesGuardEveryReferencedAccount() {
        for (mode in listOf("start", "assign")) {
            val f = fixture()
            val target = user()
            member(f.company, target)
            val employee = employee(f.browser, f.csrf, f.company)
            val id = UUID.randomUUID()
            val key = UUID.randomUUID()
            val body =
                json.writeValueAsString(
                    mapOf("expectedVersion" to 0, "assigneeId" to target, "reason" to "Assign task")
                )
            val request = {
                if (mode == "start")
                    start(
                        f.browser,
                        f.csrf,
                        f.company,
                        employee,
                        f.template,
                        id,
                        assignees = mapOf("equipment" to target),
                        key = key,
                    )
                else
                    command(
                        f.browser,
                        "${api(f.company)}/cases/${f.case}/tasks/equipment/assignee",
                        body,
                        f.csrf,
                        key,
                        "PUT",
                    )
            }
            val result =
                waiting(
                    target,
                    { database().update("update accounts set active=false where id=?", target) },
                    request,
                )
            assertEquals(422, result.statusCode(), result.body())
            assertEquals(
                "lifecycle_assignee_unavailable",
                json.readTree(result.body())["code"].asString(),
            )
            database().update("update accounts set active=true where id=?", target)
            val saved = request()
            assertEquals(200, saved.statusCode(), saved.body())
        }
    }
}
