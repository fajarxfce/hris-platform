package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseCostCenterSnapshot
import dev.fajar.hris.organization.domain.entities.OrganizationUnitSearch
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExpenseCostCenterHttpTest : ExpenseCostCenterApiFixture() {
    @ParameterizedTest
    @ValueSource(strings = ["expenses.self.manage", "expenses.manage"])
    fun expenseEditingGrantsDiscoverOnlyActiveCostCenterReferences(permission: String) {
        val f = fixture()
        val owner = member(f.company, permission)
        val first = unit(f, "CA01", "Candidate 100%")
        val second = unit(f, "CA02")
        unit(f, "CA03", active = false)
        unit(f, "DA01", kind = "DEPARTMENT")
        val otherCompany = company(f.browser, f.csrf)
        val other = unit(f.copy(company = otherCompany), "CA04")
        val path = "${f.root}/expenses/cost-centers"
        val response = get(owner.browser, "$path?query=Candidate&limit=1")
        assertEquals(200, response.statusCode(), response.body())
        val page = json.readTree(response.body())
        val reference = page["items"][0]
        assertEquals(
            setOf("id", "code", "name", "version"),
            reference.properties().map { it.key }.toSet(),
        )
        assertEquals(first.toString(), reference["id"].asString())
        assertEquals(0, reference["version"].asLong())
        assertFalse(response.body().contains(other.toString()))
        val cursor = page["nextCursor"].asString()
        assertEquals("COST_CENTER:CA01", cursor)
        val next = get(owner.browser, "$path?query=Candidate&limit=1&after=$cursor")
        assertEquals(200, next.statusCode(), next.body())
        val nextPage = json.readTree(next.body())
        assertEquals(second.toString(), nextPage["items"][0]["id"].asString())
        assertTrue(nextPage["nextCursor"].isNull)
        assertEquals(
            1,
            json.readTree(get(owner.browser, "$path?query=100%25").body())["items"].size(),
        )
        assertEquals(
            0,
            json.readTree(get(owner.browser, "$path?query=missing").body())["items"].size(),
        )
        failure(get(owner.browser, "${f.root}/organization-units"), 403, "access_denied")
        assertEquals(
            403,
            get(owner.browser, "/api/v1/companies/$otherCompany/expenses/cost-centers").statusCode(),
        )
    }

    @Test
    fun boundsAndEditingPermissionsApplyBeforeDataAcquisition() {
        val f = fixture()
        val owner = member(f.company)
        val path = "${f.root}/expenses/cost-centers"
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "after=",
                "after=CA01",
                "after=DEPARTMENT:CA01",
                "after=COST_CENTER:${"A".repeat(33)}",
            )) failure(get(owner.browser, "$path?$query"), 422, "invalid_page")
        failure(
            get(owner.browser, "$path?query=${"x".repeat(121)}"),
            422,
            "invalid_organization_search",
        )
        for (permission in
            listOf("company.read", "expenses.read", "expenses.pay", "people.self.read")) failure(
            get(member(f.company, permission).browser, path),
            403,
            "access_denied",
        )
        assertEquals(200, get(owner.browser, "$path?limit=50").statusCode())
    }

    @Test
    fun referencesUseExpenseModuleAdmissionAndNativeBuildPolicy() {
        val f = fixture()
        val owner = member(f.company)
        unit(f, "CA01")
        val path = "${f.root}/expenses/cost-centers"
        assertEquals(
            200,
            save(f, changes = mapOf("disabledModules" to listOf("PEOPLE", "WORKFORCE")))
                .statusCode(),
        )
        assertEquals(200, get(owner.browser, path).statusCode())
        assertEquals(200, save(f, 0, mapOf("disabledModules" to listOf("EXPENSES"))).statusCode())
        failure(get(owner.browser, path), 403, "company_module_disabled")
        assertEquals(
            200,
            save(f, 1, mapOf("minimumBuilds" to mapOf("android" to 12, "ios" to 0, "web" to 0)))
                .statusCode(),
        )
        val exchange =
            command(
                owner.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Expense reference fixture"}""",
                owner.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchange.statusCode(), exchange.body())
        val auth =
            "Authorization" to "Bearer ${json.readTree(exchange.body())["accessToken"].asString()}"
        failure(read(f, path, listOf(auth)), 403, "client_version_required")
        failure(
            read(
                f,
                path,
                listOf(auth, "X-HRIS-Client-Platform" to "ANDROID", "X-HRIS-Client-Build" to "11"),
            ),
            403,
            "client_update_required",
        )
        assertEquals(
            200,
            read(
                    f,
                    path,
                    listOf(
                        auth,
                        "X-HRIS-Client-Platform" to "ANDROID",
                        "X-HRIS-Client-Build" to "12",
                    ),
                )
                .statusCode(),
        )
    }

    @Test
    fun assuranceExpiryAtTheAccountGuardCannotReleaseCostCenters() {
        val f = fixture()
        val owner = member(f.company)
        val actor =
            actor(f, owner)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        unit(f, "CA01")
        val barrier = AccountLockProbe.Barrier(owner.id)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<Page<ExpenseCostCenterSnapshot>>> {
                    reader().execute(actor, "", null, 20)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accounts.current.set(null)
            }
        }
        assertTrue(
            reader().execute(actor.copy(mfaVerifiedAt = clock.instant()), "", null, 20)
                is Result.Success
        )
    }

    @Test
    fun assuranceExpiryAfterAcquisitionCannotPublishARead() {
        val f = fixture()
        val owner = member(f.company)
        val actor =
            actor(f, owner)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        unit(f, "CA01")
        val source =
            object : OrganizationRepository by units {
                override fun list(companyId: UUID, search: OrganizationUnitSearch) =
                    units.list(companyId, search).also { clock.set(clock.instant().plusSeconds(2)) }
            }
        val result = reader(source).execute(actor, "", null, 20)
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
    }

    @Test
    fun removedGrantsAndAStaleRequestCannotAcquireAnotherCapability() {
        val f = fixture()
        val owner = member(f.company)
        val actor = actor(f, owner)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=?",
                f.company,
                owner.id,
            )
        assertEquals(
            "access_denied",
            (reader().execute(actor, "", null, 20) as Result.Failed).failure.code,
        )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.manage')",
                f.company,
                owner.id,
            )
        assertEquals(
            "access_denied",
            (reader().execute(actor, "", null, 20) as Result.Failed).failure.code,
        )
        assertEquals(
            "company_required",
            (reader().execute(actor.copy(companyId = null), "", null, 20) as Result.Failed)
                .failure
                .code,
        )
        assertTrue(
            reader().execute(actor.copy(permissions = setOf("expenses.manage")), "", null, 20)
                is Result.Success
        )
    }

    @Test
    fun cancellationReleasesReadGuardsBeforeExecutorReuse() {
        val f = fixture()
        val owner = member(f.company)
        val actor = actor(f, owner)
        unit(f, "CA01")
        val barrier = AccountLockProbe.Barrier(owner.id)
        accounts.current.set(barrier)
        val thread = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<Page<ExpenseCostCenterSnapshot>>> {
                    thread.set(Thread.currentThread())
                    reader().execute(actor, "", null, 20)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                thread.get().interrupt()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertTrue(failure.cause is InterruptedException)
                accounts.current.set(null)
                val next =
                    pool
                        .submit<Result<Page<ExpenseCostCenterSnapshot>>> {
                            reader().execute(actor, "", null, 20)
                        }
                        .get(10, TimeUnit.SECONDS)
                assertEquals(1, (next as Result.Success).value.items.size)
            } finally {
                barrier.release.countDown()
                accounts.current.set(null)
            }
        }
    }
}
