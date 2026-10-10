package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.LifecycleTemplate
import dev.fajar.hris.people.domain.usecases.GetLifecycleTemplate
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class LifecycleTemplateHttpTest : LifecycleApiFixture() {
    @Autowired private lateinit var getTemplate: GetLifecycleTemplate
    @Autowired private lateinit var accounts: AccountLockProbe

    private data class Fixture(
        val admin: HttpClient,
        val csrf: String,
        val company: UUID,
        val template: UUID,
        val reader: HttpClient,
        val account: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/lifecycle/templates/$template"
    }

    @AfterEach
    fun releaseProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    private fun fixture(): Fixture {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val template = template(admin, csrf, company)
        val account = user()
        member(company, account, setOf("people.lifecycle.read"))
        val reader = client()
        login(reader, "$account@example.test")
        return Fixture(admin, csrf, company, template, reader, account)
    }

    @Test
    fun templateDetailsReturnTheCurrentDefinitionWithoutChangingExistingCases() {
        val f = fixture()
        val initial = get(f.reader, f.path)
        assertEquals(200, initial.statusCode(), initial.body())
        val body = json.readTree(initial.body())
        assertEquals(f.template.toString(), body["id"].asString())
        assertEquals(0, body["version"].asLong())
        assertEquals("ONBOARDING", body["kind"].asString())
        assertTrue(body["active"].asBoolean())
        assertEquals("equipment", body["tasks"][0]["key"].asString())
        assertEquals("Review equipment", body["tasks"][0]["title"].asString())
        assertTrue(body["tasks"][0]["required"].asBoolean())
        assertEquals(0, body["tasks"][0]["dueDays"].asInt())
        assertFalse(body.has("reason"))
        val employment = employee(f.admin, f.csrf, f.company)
        val case = case(f.admin, f.csrf, f.company, employment, f.template)
        val updated =
            saveTemplate(
                f.admin,
                f.csrf,
                f.company,
                f.template,
                version = 0,
                optional = true,
                active = false,
            )
        assertEquals(200, updated.statusCode(), updated.body())
        val current = json.readTree(get(f.reader, f.path).body())
        assertEquals(1, current["version"].asLong())
        assertFalse(current["active"].asBoolean())
        assertEquals(2, current["tasks"].size())
        val retained = json.readTree(get(f.reader, "${api(f.company)}/cases/$case").body())
        assertEquals(0, retained["templateVersion"].asLong())
        assertEquals(1, retained["tasks"].size())
    }

    @Test
    fun directLinksRequireReadPermissionAndTheSelectedCompanyScope() {
        val f = fixture()
        val otherCompany = company(f.admin, f.csrf)
        val otherTemplate = template(f.admin, f.csrf, otherCompany)
        val foreign = get(f.reader, f.path.replace(f.template.toString(), otherTemplate.toString()))
        assertEquals(404, foreign.statusCode(), foreign.body())
        assertEquals(
            "lifecycle_template_not_found",
            json.readTree(foreign.body())["code"].asString(),
        )
        assertEquals(
            403,
            get(f.reader, "${api(otherCompany)}/templates/$otherTemplate").statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=?",
                f.company,
                f.account,
            )
        for (permission in
            listOf("people.lifecycle.manage", "people.lifecycle.perform", "people.read")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                f.account,
                permission,
            )
        val denied = get(f.reader, f.path)
        assertEquals(403, denied.statusCode(), denied.body())
        assertFalse(denied.body().contains("Review equipment"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "account", "membership"])
    fun pendingReadsRecheckLiveAccessBeforeReturningTheDefinition(change: String) {
        val f = fixture()
        val gate = AccountLockProbe.Barrier(f.account)
        accounts.current.set(gate)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<HttpResponse<String>> { get(f.reader, f.path) }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "permission" ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=?",
                                f.company,
                                f.account,
                            )
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    "account" ->
                        database().update("update accounts set active=false where id=?", f.account)
                    else ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                f.company,
                                f.account,
                            )
                }
                gate.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credential" || change == "account") 401 else 403,
                    result.statusCode(),
                    result.body(),
                )
                assertFalse(result.body().contains("Review equipment"))
            } finally {
                gate.release.countDown()
            }
        }
    }

    @Test
    fun cancelledReadsReleaseTemplateAndAccessGuardsBeforeTheNextEdit() {
        val f = fixture()
        val actor =
            Actor(
                f.account,
                f.company,
                setOf("people.lifecycle.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val gate = AccountLockProbe.Barrier(f.account)
        val finished = CountDownLatch(1)
        accounts.current.set(gate)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<LifecycleTemplate>> {
                    try {
                        getTemplate.execute(actor, f.template)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertThrows(CancellationException::class.java) { pending.get(5, TimeUnit.SECONDS) }
            } finally {
                gate.release.countDown()
                accounts.current.set(null)
            }
        }
        val updated =
            saveTemplate(f.admin, f.csrf, f.company, f.template, version = 0, optional = true)
        assertEquals(200, updated.statusCode(), updated.body())
        assertEquals(1, json.readTree(get(f.reader, f.path).body())["version"].asLong())
    }
}
