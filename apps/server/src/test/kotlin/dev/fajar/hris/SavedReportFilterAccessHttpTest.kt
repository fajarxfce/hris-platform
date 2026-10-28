package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class SavedReportFilterAccessHttpTest : SavedReportFilterApiFixture() {
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    @ParameterizedTest
    @CsvSource(
        "save,lock",
        "save,find",
        "save,count",
        "save,save",
        "save,journal",
        "delete,lock",
        "delete,find",
        "delete,save",
        "delete,journal",
        "get,find",
        "list,list",
    )
    fun expiryDuringGuardsAcquisitionAndJournalCompletionNeverReturnsOrCommitsASelection(
        mode: String,
        stage: String,
    ) {
        val f = fixture()
        val id = UUID.randomUUID()
        if (mode != "save") ok(save(f, id))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val security = IdentitySecurityPolicy()
        val save =
            SaveReportFilter(
                filters,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
                operations,
                journal,
            )
        val delete =
            DeleteReportFilter(
                filters,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
                operations,
                journal,
            )
        val get =
            GetSavedReportFilter(
                filters,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val list =
            ListSavedReportFilters(
                filters,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val operation = UUID.randomUUID()
        fun invoke(current: Actor): Result<*> =
            when (mode) {
                "save" -> save.execute(current, id, operation, definition(), null)
                "delete" -> delete.execute(current, id, operation, 0)
                "get" -> get.execute(current, id)
                else -> list.execute(current)
            }
        val result =
            waiting(f, stage, { clock.set(clock.instant().plusSeconds(2)) }, { invoke(actor) })
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
        assertEquals(if (mode == "save") listOf(0, 0, 0) else listOf(1, 1, 1), evidence(f, id))
        val retained =
            (transactions.run(f.actor) { filters.find(f.company, f.account, id) } as Result.Success)
                .value
        if (mode == "save") assertNull(retained)
        else {
            assertNotNull(retained?.definition)
            assertEquals(0L, retained?.version)
        }
        assertInstanceOf(
            Result.Success::class.java,
            invoke(actor.copy(mfaVerifiedAt = clock.instant())),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["report", "source", "company", "membership", "account", "credential"])
    fun waitingCommandsAndExistingReceiptsRecheckCurrentAuthority(mode: String) {
        for (replay in listOf(false, true)) {
            val f = fixture()
            val id = UUID.randomUUID()
            val operation = UUID.randomUUID()
            if (replay) ok(save(f, id, operation = operation))
            val result =
                waiting(
                    f,
                    "lock",
                    {
                        when (mode) {
                            "report" -> revoke(f, "reports.read")
                            "source" -> revoke(f, "people.read")
                            "company" ->
                                database()
                                    .update(
                                        "update companies set active=false where id=?",
                                        f.company,
                                    )
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false where company_id=? and account_id=?",
                                        f.company,
                                        f.account,
                                    )
                            "account" ->
                                database()
                                    .update(
                                        "update accounts set active=false where id=?",
                                        f.account,
                                    )
                            else ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.account,
                                    )
                        }
                    },
                    { saveFilter.execute(f.actor, id, operation, definition(), null) },
                )
            assertEquals(
                when (mode) {
                    "report",
                    "source" -> "access_denied"
                    "company",
                    "membership" -> "company_access_denied"
                    else -> "session_revoked"
                },
                (result as Result.Failed).failure.code,
            )
            assertEquals(if (replay) listOf(1, 1, 1) else listOf(0, 0, 0), evidence(f, id))
        }
    }

    @Test
    fun reportingClientAdmissionAppliesToEverySavedFilterEndpoint() {
        val f = fixture()
        val id = UUID.randomUUID()
        ok(save(f, id))
        ok(
            command(
                f.browser,
                "/api/v1/companies/${f.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause saved filter access"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        for (response in
            listOf(
                get(f.browser, base(f)),
                get(f.browser, "${base(f)}/$id"),
                save(f, id, 0),
                delete(f, id, 0),
            )) failure(response, 403, "company_module_disabled")
        assertEquals(listOf(1, 1, 1), evidence(f, id))
    }
}
