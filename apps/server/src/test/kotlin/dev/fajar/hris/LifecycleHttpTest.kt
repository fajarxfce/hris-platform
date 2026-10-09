package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException

@Import(AccountLockProbeConfiguration::class)
class LifecycleHttpTest : LifecycleApiFixture() {
    @Autowired private lateinit var accessProbe: AccountLockProbe

    @Test
    fun templatesAreFrozenRequiredTasksBlockCompletionAndEveryChangeHasImmutableHistory() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val template = template(browser, csrf, company, optional = true)
        val id = case(browser, csrf, company, employee, template)
        assertEquals(
            200,
            saveTemplate(browser, csrf, company, template, version = 0, active = false).statusCode(),
        )
        val frozen = json.readTree(get(browser, "${api(company)}/cases/$id").body())
        assertEquals(2, frozen.get("tasks").size())
        assertEquals(0, frozen.get("templateVersion").asLong())
        assertEquals(422, change(browser, csrf, company, id, 0, "WAIVED").statusCode())
        assertEquals(409, finish(browser, csrf, company, id, 0).statusCode())
        val key = UUID.randomUUID()
        val changed = change(browser, csrf, company, id, 0, key = key)
        assertEquals(200, changed.statusCode(), changed.body())
        assertEquals(409, finish(browser, csrf, company, id, 1).statusCode())
        assertEquals(200, change(browser, csrf, company, id, 1, "WAIVED", "welcome").statusCode())
        val completionKey = UUID.randomUUID()
        val completed = finish(browser, csrf, company, id, 2, key = completionKey)
        assertEquals(200, completed.statusCode(), completed.body())
        assertEquals(
            completed.body(),
            finish(browser, csrf, company, id, 2, key = completionKey).body(),
        )
        assertEquals(changed.body(), change(browser, csrf, company, id, 0, key = key).body())
        assertEquals(
            "COMPLETED",
            json.readTree(get(browser, "${api(company)}/cases/$id").body()).get("status").asString(),
        )
        assertEquals(409, change(browser, csrf, company, id, 3, "PENDING").statusCode())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from employments where company_id=? and id=?",
                    Int::class.java,
                    company,
                    employee,
                ),
        )
        val history =
            json.readTree(get(browser, "${api(company)}/cases/$id/history?limit=2").body())
        assertEquals(2, history.get("items").size())
        assertEquals("1", history.get("nextCursor").asString())
        assertEquals(
            2,
            json
                .readTree(get(browser, "${api(company)}/cases/$id/history?after=1").body())
                .get("items")
                .size(),
        )
        assertThrows(DataAccessException::class.java) {
            database().update("delete from lifecycle_events where case_id=?", id)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update lifecycle_tasks set title='Different requirement' where case_id=?",
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from lifecycle_template_revisions where id=?", template)
        }
    }

    @Test
    fun assigneesSeeOnlyTheirOwnQueueAndReassignmentOrRevocationRemovesAuthority() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val otherCompany = company(admin, csrf)
        val assigned = user()
        val other = user()
        member(company, assigned)
        member(company, other)
        val template = template(admin, csrf, company)
        val employee = employee(admin, csrf, company)
        val first =
            case(
                admin,
                csrf,
                company,
                employee,
                template,
                assignees = mapOf("equipment" to assigned),
            )
        val second =
            case(
                admin,
                csrf,
                company,
                employee(admin, csrf, company),
                template,
                assignees = mapOf("equipment" to other),
            )
        val browser = client()
        val userCsrf = login(browser, "$assigned@example.test")
        val queue = get(browser, "${api(company)}/tasks/assigned?limit=1")
        assertEquals(200, queue.statusCode(), queue.body())
        assertEquals(
            first.toString(),
            json.readTree(queue.body()).get("items").get(0).get("caseId").asString(),
        )
        assertEquals(403, get(browser, "${api(company)}/cases/$first").statusCode())
        assertEquals(403, change(browser, userCsrf, company, second, 0).statusCode())
        assertEquals(404, get(admin, "${api(otherCompany)}/cases/$first").statusCode())
        assertEquals(422, start(admin, csrf, otherCompany, employee, template).statusCode())
        val assignment =
            command(
                admin,
                "${api(company)}/cases/$first/tasks/equipment/assignee",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "assigneeId" to other,
                        "reason" to "New task owner",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, assignment.statusCode(), assignment.body())
        assertEquals(403, change(browser, userCsrf, company, first, 1).statusCode())
        assertEquals(
            0,
            json.readTree(get(browser, "${api(company)}/tasks/assigned").body()).get("items").size(),
        )
        val delegate = client()
        val delegateCsrf = login(delegate, "$other@example.test")
        assertEquals(200, change(delegate, delegateCsrf, company, first, 1).statusCode())
        database()
            .update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                company,
                other,
            )
        assertEquals(403, change(delegate, delegateCsrf, company, second, 0).statusCode())
        assertEquals(403, get(delegate, "${api(company)}/tasks/assigned").statusCode())
        assertEquals(422, get(admin, "${api(company)}/tasks/assigned?after=invalid").statusCode())
    }

    @Test
    fun offboardingEndsOnlyTheEmploymentAndItsUnusedCompanyMembership() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val otherCompany = company(admin, csrf)
        val account = user()
        member(company, account)
        member(otherCompany, account)
        val employee = employee(admin, csrf, company, account = account)
        val template = template(admin, csrf, company, "OFFBOARDING")
        val id = case(admin, csrf, company, employee, template, "2026-09-30")
        val browser = client()
        login(browser, "$account@example.test")
        assertEquals(409, finish(admin, csrf, company, id, 0, true).statusCode())
        assertEquals(200, change(admin, csrf, company, id, 0).statusCode())
        val key = UUID.randomUUID()
        val result = finish(admin, csrf, company, id, 1, true, key = key)
        assertEquals(200, result.statusCode(), result.body())
        assertEquals(result.body(), finish(admin, csrf, company, id, 1, true, key = key).body())
        assertEquals(403, get(browser, "/api/v1/companies/$company/me/access").statusCode())
        assertEquals(200, get(browser, "/api/v1/companies/$otherCompany/me/access").statusCode())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Int::class.java,
                    account,
                ),
        )
        val ended =
            json.readTree(
                get(admin, "/api/v1/companies/$company/employees/$employee?asOf=2026-10-01").body()
            )
        assertEquals("ENDED", ended.get("terms").get("status").asString())
        assertEquals("2026-09-30", ended.get("terms").get("endDate").asString())
        assertEquals(
            "ACTIVE",
            json
                .readTree(
                    get(admin, "/api/v1/companies/$company/employees/$employee?asOf=2026-09-30")
                        .body()
                )
                .get("terms")
                .get("status")
                .asString(),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from membership_role_applications where company_id=? and account_id=? and membership_version=1",
                    Int::class.java,
                    company,
                    account,
                ),
        )
        assertEquals(
            409,
            revise(admin, csrf, company, employee, 1, terms("2026-10-02")).statusCode(),
        )
        assertEquals(
            409,
            start(admin, csrf, company, employee, template, date = "2026-09-30").statusCode(),
        )
    }

    @Test
    fun failedOffboardingRollsBackEmploymentAccessHistoryAndReceiptBeforeRetry() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val account = user()
        member(company, account)
        val employee = employee(browser, csrf, company, account = account)
        val template = template(browser, csrf, company, "OFFBOARDING")
        val id = case(browser, csrf, company, employee, template, "2026-09-30")
        assertEquals(200, change(browser, csrf, company, id, 0).statusCode())
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_offboarding_audit() returns trigger language plpgsql as ${'$'}${'$'} begin
            if new.resource_id='$employee'::uuid and new.action='people.offboarding_completed' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger lifecycle_probe before insert on audit_entries for each row execute function fail_offboarding_audit()"
            )
        try {
            assertEquals(409, finish(browser, csrf, company, id, 1, true, key = key).statusCode())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select version from employments where company_id=? and id=?",
                        Int::class.java,
                        company,
                        employee,
                    ),
            )
            assertEquals(
                true,
                database()
                    .queryForObject(
                        "select active from company_memberships where company_id=? and account_id=?",
                        Boolean::class.java,
                        company,
                        account,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from membership_role_applications where company_id=? and account_id=?",
                        Int::class.java,
                        company,
                        account,
                    ),
            )
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select version from lifecycle_cases where company_id=? and id=?",
                        Int::class.java,
                        company,
                        id,
                    ),
            )
            assertEquals(
                2,
                database()
                    .queryForObject(
                        "select count(*) from lifecycle_events where company_id=? and case_id=?",
                        Int::class.java,
                        company,
                        id,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
        } finally {
            database().execute("drop trigger lifecycle_probe on audit_entries")
            database().execute("drop function fail_offboarding_audit()")
        }
        assertEquals(200, finish(browser, csrf, company, id, 1, true, key = key).statusCode())
    }

    @Test
    fun concurrentTaskReopeningAndFinalizationCannotCompleteAnUnresolvedCase() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val template = template(browser, csrf, company, "OFFBOARDING")
        val id = case(browser, csrf, company, employee, template, "2026-09-30")
        assertEquals(200, change(browser, csrf, company, id, 0).statusCode())
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val completed =
                pool.submit<Int> {
                    ready.countDown()
                    check(go.await(5, TimeUnit.SECONDS))
                    finish(browser, csrf, company, id, 1, true).statusCode()
                }
            val reopened =
                pool.submit<Int> {
                    ready.countDown()
                    check(go.await(5, TimeUnit.SECONDS))
                    change(browser, csrf, company, id, 1, "PENDING").statusCode()
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertEquals(
                listOf(200, 409),
                listOf(completed.get(15, TimeUnit.SECONDS), reopened.get(15, TimeUnit.SECONDS))
                    .sorted(),
            )
        }
        val state = json.readTree(get(browser, "${api(company)}/cases/$id").body())
        val complete = state.get("status").asString() == "COMPLETED"
        assertEquals(
            if (complete) "DONE" else "PENDING",
            state.get("tasks").get(0).get("status").asString(),
        )
        assertEquals(
            if (complete) 1 else 0,
            database()
                .queryForObject(
                    "select version from employments where company_id=? and id=?",
                    Int::class.java,
                    company,
                    employee,
                ),
        )
        assertEquals(2, state.get("version").asLong())
    }

    @Test
    fun dateAccessAndScheduledWorkMustBeResolvedBeforeOffboarding() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf, "America/Los_Angeles")
        val employee = employee(browser, csrf, company)
        val template = template(browser, csrf, company, "OFFBOARDING")
        val id = case(browser, csrf, company, employee, template, "2026-09-30")
        assertEquals(200, change(browser, csrf, company, id, 0).statusCode())
        val early = finish(browser, csrf, company, id, 1, true)
        assertEquals(409, early.statusCode())
        assertEquals(
            "offboarding_date_not_reached",
            json.readTree(early.body()).get("code").asString(),
        )
        clock.set(Instant.parse("2026-10-01T07:01:00Z"))
        val fresh = login(browser)
        assertEquals(
            200,
            revise(browser, fresh, company, employee, 0, terms("2026-11-01")).statusCode(),
        )
        val future = finish(browser, fresh, company, id, 1, true, 1)
        assertEquals(409, future.statusCode())
        assertEquals(
            "scheduled_employment_changes_pending",
            json.readTree(future.body()).get("code").asString(),
        )
        assertEquals(200, cancellation(browser, fresh, company, employee, 1, 1).statusCode())
        val report = employee(browser, fresh, company, manager = employee)
        val assigned = finish(browser, fresh, company, id, 1, true, 2)
        assertEquals(409, assigned.statusCode())
        assertEquals(
            "reporting_reassignment_required",
            json.readTree(assigned.body()).get("code").asString(),
        )
        assertEquals(
            200,
            revise(browser, fresh, company, report, 0, terms("2026-10-01")).statusCode(),
        )
        assertEquals(200, finish(browser, fresh, company, id, 1, true, 2).statusCode())
    }

    @Test
    fun openLifecycleCasesAndCompanyTransfersAreSerialized() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val target = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val template = template(browser, csrf, company)
        val id = case(browser, csrf, company, employee, template)
        val blocked = transfer(browser, csrf, company, employee, target)
        assertEquals(409, blocked.statusCode())
        assertEquals(
            "open_lifecycle_cases_pending",
            json.readTree(blocked.body()).get("code").asString(),
        )
        assertEquals(200, cancel(browser, csrf, company, id, 0).statusCode())
        assertEquals(200, transfer(browser, csrf, company, employee, target).statusCode())
        val second = employee(browser, csrf, company)
        val secondCase = UUID.randomUUID()
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val opened =
                pool.submit<Int> {
                    ready.countDown()
                    check(go.await(5, TimeUnit.SECONDS))
                    start(browser, csrf, company, second, template, secondCase).statusCode()
                }
            val transferred =
                pool.submit<Int> {
                    ready.countDown()
                    check(go.await(5, TimeUnit.SECONDS))
                    transfer(browser, csrf, company, second, target).statusCode()
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            val outcomes =
                listOf(opened.get(15, TimeUnit.SECONDS), transferred.get(15, TimeUnit.SECONDS))
            assertEquals(1, outcomes.count { it == 200 })
            assertTrue(outcomes.all { it in setOf(200, 409, 422) })
        }
        val caseCount =
            database()
                .queryForObject(
                    "select count(*) from lifecycle_cases where id=?",
                    Int::class.java,
                    secondCase,
                )!!
        val transfers =
            database()
                .queryForObject(
                    "select count(*) from employment_transfers where source_company_id=? and source_employment_id=?",
                    Int::class.java,
                    company,
                    second,
                )!!
        assertEquals(1, caseCount + transfers)
    }

    @Test
    fun offboardingPreservesOtherSourceEmploymentAndRejectsSelfOrLastAdminRemoval() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val template = template(browser, csrf, company, "OFFBOARDING")
        val account = user()
        member(company, account)
        val employee = employee(browser, csrf, company, account = account)
        val person =
            database()
                .queryForObject(
                    "select person_id from employments where company_id=? and id=?",
                    UUID::class.java,
                    company,
                    employee,
                )!!
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        val other = UUID.randomUUID()
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,'NEXT-ROLE')",
                company,
                other,
                person,
            )
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason) values(?,?,0,'2027-01-01','PERMANENT','2027-01-01','ACTIVE',?,'Planned role')",
                company,
                other,
                admin,
            )
        val id = case(browser, csrf, company, employee, template, "2026-09-30")
        assertEquals(200, change(browser, csrf, company, id, 0).statusCode())
        assertEquals(200, finish(browser, csrf, company, id, 1, true).statusCode())
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    company,
                    account,
                ),
        )
        val own = employee(browser, csrf, company, account = admin)
        val ownCase = case(browser, csrf, company, own, template, "2026-09-30")
        assertEquals(200, change(browser, csrf, company, ownCase, 0).statusCode())
        assertEquals(403, finish(browser, csrf, company, ownCase, 1, true).statusCode())
        val last = user()
        member(company, last, setOf("company.read", "identity.manage"))
        val lastEmployee = employee(browser, csrf, company, account = last)
        val lastCase = case(browser, csrf, company, lastEmployee, template, "2026-09-30")
        assertEquals(200, change(browser, csrf, company, lastCase, 0).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='identity.manage'",
                company,
                admin,
            )
        val denied = finish(browser, csrf, company, lastCase, 1, true)
        assertEquals(409, denied.statusCode())
        assertEquals(
            "last_company_administrator",
            json.readTree(denied.body()).get("code").asString(),
        )
    }

    @Test
    fun offboardingRequiresOtherLifecycleCasesResolvedAndRechecksRevokedCredentials() {
        val browser = client()
        var csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val onboardingTemplate = template(browser, csrf, company)
        val offboardingTemplate = template(browser, csrf, company, "OFFBOARDING")
        val onboarding = case(browser, csrf, company, employee, onboardingTemplate)
        val departure = case(browser, csrf, company, employee, offboardingTemplate, "2026-09-30")
        assertEquals(200, change(browser, csrf, company, departure, 0).statusCode())
        val blocked = finish(browser, csrf, company, departure, 1, true)
        assertEquals(409, blocked.statusCode())
        assertEquals(
            "other_lifecycle_cases_pending",
            json.readTree(blocked.body()).get("code").asString(),
        )
        assertEquals(200, cancel(browser, csrf, company, onboarding, 0).statusCode())
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        val barrier = AccountLockProbe.Barrier(admin)
        accessProbe.current.set(barrier)
        val key = UUID.randomUUID()
        Executors.newSingleThreadExecutor().use { pool ->
            val request =
                pool.submit<Int> {
                    finish(browser, csrf, company, departure, 1, true, key = key).statusCode()
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        admin,
                    )
            } finally {
                barrier.release.countDown()
                accessProbe.current.set(null)
            }
            assertEquals(401, request.get(10, TimeUnit.SECONDS))
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from employments where company_id=? and id=?",
                    Int::class.java,
                    company,
                    employee,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select version from lifecycle_cases where company_id=? and id=?",
                    Int::class.java,
                    company,
                    departure,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    key,
                ),
        )
        csrf = login(browser)
        assertEquals(
            200,
            finish(browser, csrf, company, departure, 1, true, key = key).statusCode(),
        )
    }
}
