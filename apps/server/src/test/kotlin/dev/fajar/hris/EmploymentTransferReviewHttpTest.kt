package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.people.domain.usecases.GetEmploymentTransferReview
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EmploymentTransferReviewHttpTest : ClientPolicyApiFixture() {
    @Autowired private lateinit var review: GetEmploymentTransferReview

    @Test
    fun reviewReturnsCurrentVersionCompanyDatesAndDirectoryFieldsWithoutMutation() {
        val source = fixture()
        val target = company(source.browser, source.csrf, "Asia/Makassar")
        val employee = employee(source.browser, source.csrf, source.company)
        assertEquals(
            200,
            revise(source.browser, source.csrf, source.company, employee, 0, terms("2026-08-01"))
                .statusCode(),
        )
        val response =
            get(
                source.browser,
                "${source.root}/employees/$employee/transfer-review?targetCompanyId=$target",
            )
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(employee.toString(), body["employee"]["id"].asString())
        assertEquals(1, body["employee"]["version"].asInt())
        assertEquals(source.company.toString(), body["sourceCompany"]["id"].asString())
        assertEquals(target.toString(), body["targetCompany"]["id"].asString())
        assertEquals("Asia/Makassar", body["targetCompany"]["timezone"].asString())
        assertEquals("2026-10-01", body["sourceDate"].asString())
        assertEquals("2026-10-01", body["targetDate"].asString())
        assertEquals(clock.instant().toString(), body["evaluatedAt"].asString())
        assertTrue(
            body["targetPermissions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList()
                .containsAll(listOf("people.read", "people.manage", "people.transfer"))
        )
        assertFalse(body["employee"]["person"].has("birthDate"))
        assertFalse(body["employee"]["person"].has("nationality"))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employment_transfers where source_company_id=?",
                    Int::class.java,
                    source.company,
                ),
        )
    }

    @Test
    fun reviewRejectsForeignEmploymentSameCompanyAndIncompatibleDates() {
        val source = fixture()
        val target = company(source.browser, source.csrf)
        val earlierDate = company(source.browser, source.csrf, "America/Los_Angeles")
        val employee = employee(source.browser, source.csrf, source.company)
        val today = employee(source.browser, source.csrf, source.company, start = "2026-10-01")
        failure(
            get(
                source.browser,
                "${source.root}/employees/$employee/transfer-review?targetCompanyId=${source.company}",
            ),
            422,
            "invalid_employment_transfer",
        )
        failure(
            get(
                source.browser,
                "${source.root}/employees/$employee/transfer-review?targetCompanyId=$earlierDate",
            ),
            422,
            "transfer_requires_current_company_date",
        )
        failure(
            get(
                source.browser,
                "${source.root}/employees/$today/transfer-review?targetCompanyId=$target",
            ),
            409,
            "source_employment_not_transferable",
        )
        failure(
            get(
                source.browser,
                "/api/v1/companies/$target/employees/$employee/transfer-review?targetCompanyId=${source.company}",
            ),
            404,
            "employee_not_found",
        )
    }

    @Test
    fun destinationModuleAvailabilityAlsoGatesReview() {
        val source = fixture()
        val target = source.copy(company = company(source.browser, source.csrf))
        val employee = employee(source.browser, source.csrf, source.company)
        assertEquals(
            200,
            save(target, changes = mapOf("disabledModules" to listOf("PEOPLE"))).statusCode(),
        )
        val response =
            get(
                source.browser,
                "${source.root}/employees/$employee/transfer-review?targetCompanyId=${target.company}",
            )
        failure(response, 403, "company_module_disabled")
        assertEquals(
            target.company.toString(),
            json.readTree(response.body())["parameters"]["companyId"].asString(),
        )
    }

    @Test
    fun aDestinationGrantAddedDuringReviewCannotExpandItsAssignmentChoices() {
        val source = fixture()
        val target = company(source.browser, source.csrf)
        val employee = employee(source.browser, source.csrf, source.company)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='company.read'",
                target,
                source.account,
            )
        val path = "${source.root}/employees/$employee/transfer-review?targetCompanyId=$target"
        val barrier = ClientPolicyProbe.Barrier(target, shared = true)
        policyProbe.beforeLock.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> { get(source.browser, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'company.read')",
                        target,
                        source.account,
                    )
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode(), response.body())
                assertFalse(
                    json
                        .readTree(response.body())["targetPermissions"]
                        .iterator()
                        .asSequence()
                        .map { it.asString() }
                        .contains("company.read")
                )
            } finally {
                policyProbe.clear()
            }
        }
        assertTrue(
            json
                .readTree(get(source.browser, path).body())["targetPermissions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .contains("company.read")
        )
    }

    @Test
    fun removingDestinationTransferAuthorityWhileWaitingRejectsReview() {
        val source = fixture()
        val target = company(source.browser, source.csrf)
        val employee = employee(source.browser, source.csrf, source.company)
        val path = "${source.root}/employees/$employee/transfer-review?targetCompanyId=$target"
        val barrier = ClientPolicyProbe.Barrier(target, shared = true)
        policyProbe.beforeLock.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> { get(source.browser, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='people.transfer'",
                        target,
                        source.account,
                    )
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                failure(response, 403, "transfer_access_required")
                assertEquals(
                    target.toString(),
                    json.readTree(response.body())["parameters"]["companyId"].asString(),
                )
            } finally {
                policyProbe.clear()
            }
        }
    }

    @Test
    fun cancellationAfterPolicyAcquisitionReleasesBothCompanyGuardsAndTheAccount() {
        val source = fixture()
        val target = source.copy(company = company(source.browser, source.csrf))
        val employee = employee(source.browser, source.csrf, source.company)
        val barrier = ClientPolicyProbe.Barrier(target.company, shared = true)
        val finished = CountDownLatch(1)
        policyProbe.afterEffectiveRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit {
                    try {
                        review.execute(
                            source.actor.copy(
                                permissions =
                                    setOf("people.read", "people.manage", "people.transfer")
                            ),
                            employee,
                            target.company,
                            ClientRequest(null, native = false),
                        )
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals(
                    1,
                    database()
                        .queryForObject(
                            "select count(*) from (select id from accounts where id=? for update nowait) unlocked",
                            Int::class.java,
                            source.account,
                        ),
                )
            } finally {
                policyProbe.clear()
            }
        }
        assertEquals(200, save(target).statusCode())
        assertEquals(200, save(source).statusCode())
        val next =
            get(
                source.browser,
                "${source.root}/employees/$employee/transfer-review?targetCompanyId=${target.company}",
            )
        assertEquals(200, next.statusCode(), next.body())
    }
}
