package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(LifecycleReadProbeConfiguration::class)
class OffboardingReviewHttpTest : LifecycleApiFixture() {
    @Autowired private lateinit var probe: LifecycleReadProbe

    private val permissions =
        setOf(
            "people.lifecycle.read",
            "people.lifecycle.manage",
            "people.manage",
            "people.offboard",
        )

    @Test
    fun reviewRetainsCompanyDateAndBothVersionsWithoutAcquiringAPrivateProfile() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf, "America/Los_Angeles")
        val employee = employee(browser, csrf, company)
        val template = template(browser, csrf, company, "OFFBOARDING")
        val id = case(browser, csrf, company, employee, template, "2026-09-29")
        val reviewer = user()
        member(company, reviewer, permissions)
        val reader = client()
        val readerCsrf = login(reader, "$reviewer@example.test")
        val path = "${api(company)}/cases/$id/offboarding-review"
        val response = get(reader, path)
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(
            setOf("case", "employmentVersion", "today"),
            body.properties().map { it.key }.toSet(),
        )
        assertEquals("2026-09-30", body["today"].asString())
        assertEquals(0, body["employmentVersion"].asLong())
        assertEquals(0, body["case"]["version"].asLong())
        assertEquals(id.toString(), body["case"]["id"].asString())
        assertEquals("PENDING", body["case"]["tasks"][0]["status"].asString())
        assertEquals(
            setOf("id", "employeeNumber", "name"),
            body["case"]["employee"].properties().map { it.key }.toSet(),
        )
        assertEquals(employee.toString(), body["case"]["employee"]["id"].asString())
        for (privateField in
            listOf("birthDate", "nationality", "personId", "accountId", "email")) assertFalse(
            response.body().contains("\"$privateField\""),
            privateField,
        )
        val hiddenProfile = get(reader, "/api/v1/companies/$company/employees/$employee/profile")
        assertEquals(404, hiddenProfile.statusCode(), hiddenProfile.body())

        val revised = revise(browser, csrf, company, employee, 0, terms("2026-09-15"))
        assertEquals(200, revised.statusCode(), revised.body())
        assertEquals(200, change(browser, csrf, company, id, 0).statusCode())
        val current = json.readTree(get(reader, path).body())
        assertEquals(1, current["employmentVersion"].asLong())
        assertEquals(1, current["case"]["version"].asLong())
        assertEquals("DONE", current["case"]["tasks"][0]["status"].asString())
        val stale = finish(reader, readerCsrf, company, id, 1, offboarding = true)
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("stale_employment_version", json.readTree(stale.body())["code"].asString())
        val completed =
            finish(reader, readerCsrf, company, id, 1, offboarding = true, employmentVersion = 1)
        assertEquals(200, completed.statusCode(), completed.body())
        assertEquals(2, json.readTree(completed.body())["version"].asLong())
        val closed = get(reader, path)
        assertEquals(409, closed.statusCode(), closed.body())
        assertEquals("lifecycle_case_not_open", json.readTree(closed.body())["code"].asString())
        val history = json.readTree(get(reader, "${api(company)}/cases/$id/history").body())
        assertEquals(
            listOf("CREATED", "TASK_DONE", "COMPLETED"),
            history["items"].iterator().asSequence().map { it["action"].asString() }.toList(),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            ["people.lifecycle.read", "people.lifecycle.manage", "people.manage", "people.offboard"]
    )
    fun reviewRequiresEveryReadAndOffboardingGrant(missing: String) {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val id =
            case(browser, csrf, company, employee, template(browser, csrf, company, "OFFBOARDING"))
        val reviewer = user()
        member(company, reviewer, permissions - missing)
        val reader = client()
        login(reader, "$reviewer@example.test")
        val denied = get(reader, "${api(company)}/cases/$id/offboarding-review")
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("offboarding_access_required", json.readTree(denied.body())["code"].asString())
        assertFalse(denied.body().contains("Example employee"))
    }

    @Test
    fun foreignOnboardingAndCancelledCasesCannotSupplyAnOffboardingReview() {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val other = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val id =
            case(browser, csrf, company, employee, template(browser, csrf, company, "OFFBOARDING"))
        val foreign = get(browser, "${api(other)}/cases/$id/offboarding-review")
        assertEquals(404, foreign.statusCode(), foreign.body())
        assertEquals("lifecycle_case_not_found", json.readTree(foreign.body())["code"].asString())
        assertFalse(foreign.body().contains("Example employee"))
        val onboarding = case(browser, csrf, company, employee, template(browser, csrf, company))
        val wrongKind = get(browser, "${api(company)}/cases/$onboarding/offboarding-review")
        assertEquals(409, wrongKind.statusCode(), wrongKind.body())
        assertEquals("lifecycle_case_not_open", json.readTree(wrongKind.body())["code"].asString())
        assertEquals(200, cancel(browser, csrf, company, id, 0).statusCode())
        val cancelled = get(browser, "${api(company)}/cases/$id/offboarding-review")
        assertEquals(409, cancelled.statusCode(), cancelled.body())
        assertEquals("lifecycle_case_not_open", json.readTree(cancelled.body())["code"].asString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["grant", "membership", "credentials"])
    fun accessIsRecheckedAfterWaitingForTheCase(change: String) {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val id =
            case(browser, csrf, company, employee, template(browser, csrf, company, "OFFBOARDING"))
        val reviewer = user()
        member(company, reviewer, permissions)
        val reader = client()
        login(reader, "$reviewer@example.test")
        val barrier = LifecycleReadProbe.Barrier(company)
        probe.caseWait.set(barrier)
        val denied =
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<HttpResponse<String>> {
                        get(reader, "${api(company)}/cases/$id/offboarding-review")
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (change) {
                        "grant" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='people.offboard'",
                                    company,
                                    reviewer,
                                )
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    company,
                                    reviewer,
                                )
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    reviewer,
                                )
                    }
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    probe.caseWait.set(null)
                }
            }
        assertEquals(if (change == "credentials") 401 else 403, denied.statusCode(), denied.body())
        assertEquals(
            when (change) {
                "grant" -> "offboarding_access_required"
                "membership" -> "company_access_denied"
                else -> "session_revoked"
            },
            json.readTree(denied.body())["code"].asString(),
        )
        assertFalse(denied.body().contains("Example employee"))
    }
}
