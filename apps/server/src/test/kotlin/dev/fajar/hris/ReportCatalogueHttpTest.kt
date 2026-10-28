package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.usecases.GetReportCatalogue
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class ReportCatalogueHttpTest : HeadcountReportApiFixture() {
    @Autowired private lateinit var catalogue: GetReportCatalogue
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private fun path(f: Fixture) = "/api/v1/companies/${f.company}/reports/catalogue"

    private fun kinds(f: Fixture): List<String> {
        val response = get(f.browser, path(f))
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        assertTrue(response.body().length < 2048)
        val body = json.readTree(response.body())
        assertEquals(f.company.toString(), body["companyId"].asString())
        assertEquals(clock.instant().toString(), body["evaluatedAt"].asString())
        val reports = body["reports"]
        return (0 until reports.size()).map { index ->
            val it = reports[index]
            val kind = ReportKind.valueOf(it["kind"].asString())
            assertEquals(kind.definitionVersion, it["definitionVersion"].asString())
            assertEquals(setOf("kind", "definitionVersion"), it.propertyNames().toSet())
            kind.name
        }
    }

    private fun grant(f: Fixture, permission: String) {
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?) on conflict do nothing",
                f.company,
                f.account,
                permission,
            )
    }

    private fun revoke(f: Fixture, permission: String) {
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                f.company,
                f.account,
                permission,
            )
    }

    private fun waiting(
        f: Fixture,
        reader: GetReportCatalogue = catalogue,
        actor: Actor = f.actor,
        change: () -> Unit,
    ): Result<ReportCatalogue> {
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<ReportCatalogue>> { reader.execute(actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun discoveryContainsOnlyCurrentDefinitionsAndNeverAcquiresOperationalFacts() {
        val f = fixture()
        grant(f, "payroll.read")
        reportProbe.failure.set(
            IllegalStateException("Report facts must not be loaded for discovery")
        )
        assertEquals(ReportKind.entries.map { it.name }, kinds(f))
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission<>'reports.read'",
                f.company,
                f.account,
            )
        for (permission in
            listOf(
                "people.team.read",
                "workforce.team.read",
                "overtime.self.manage",
                "expenses.team.read",
                "payroll.self.read",
            )) grant(f, permission)
        assertTrue(kinds(f).isEmpty())
        grant(f, "people.read")
        assertEquals(listOf("HEADCOUNT"), kinds(f))
        revoke(f, "reports.read")
        failure(get(f.browser, path(f)), 403, "access_denied")
    }

    @Test
    fun companyAndModuleAdmissionApplyToCatalogueWithoutSourceData() {
        val f = fixture()
        val other = fixture()
        failure(get(f.browser, path(other)), 403, "company_access_denied")
        val settings =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause report discovery"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, settings.statusCode(), settings.body())
        failure(get(f.browser, path(f)), 403, "company_module_disabled")
        assertEquals(
            "company_required",
            (catalogue.execute(f.actor.copy(companyId = null)) as Result.Failed).failure.code,
        )
    }

    @Test
    fun catalogueIntersectsOriginalAndLiveSourceGrantsAfterWaiting() {
        val f = fixture()
        revoke(f, "workforce.read")
        val gain = waiting(f) { grant(f, "workforce.read") }
        assertEquals(listOf(ReportKind.HEADCOUNT), (gain as Result.Success).value.reports)
        val loss = waiting(f) { revoke(f, "people.read") }
        assertTrue((loss as Result.Success).value.reports.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["report", "company", "membership", "account", "credential"])
    fun admissionRemovedDuringWaitCannotPublishCatalogue(mode: String) {
        val f = fixture()
        val result =
            waiting(f) {
                when (mode) {
                    "report" -> revoke(f, "reports.read")
                    "company" ->
                        database().update("update companies set active=false where id=?", f.company)
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                f.company,
                                f.account,
                            )
                    "account" ->
                        database().update("update accounts set active=false where id=?", f.account)
                    else ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                }
            }
        assertEquals(
            when (mode) {
                "report" -> "access_denied"
                "company",
                "membership" -> "company_access_denied"
                else -> "session_revoked"
            },
            (result as Result.Failed).failure.code,
        )
    }

    @Test
    fun expiredMfaDuringWaitRequiresRenewalBeforeDiscovery() {
        val f = fixture()
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val security = IdentitySecurityPolicy()
        val reader =
            GetReportCatalogue(companies, members, identities, transactions, security, clock)
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val result = waiting(f, reader, actor) { clock.set(clock.instant().plusSeconds(2)) }
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
        assertInstanceOf(
            Result.Success::class.java,
            reader.execute(actor.copy(mfaVerifiedAt = clock.instant())),
        )
    }

    @Test
    fun interruptedDiscoveryReleasesGuardsForTheNextRead() {
        val f = fixture()
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<ReportCatalogue>> { catalogue.execute(f.actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertInstanceOf(Result.Success::class.java, catalogue.execute(f.actor))
    }
}
