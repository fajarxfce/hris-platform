package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.policies.employeeImportPermissions
import dev.fajar.hris.people.domain.repositories.EmployeeImportRepository
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class EmployeeImportAssuranceHttpTest : EmployeeImportApiFixture() {
    @Autowired private lateinit var imports: EmployeeImportRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var accountProbe: AccountLockProbe
    @Autowired private lateinit var jobs: JobRepository

    @ParameterizedTest
    @ValueSource(strings = ["list", "summary", "rows", "attempts"])
    fun readsRejectProofThatExpiresDuringAccessAcquisitionAndRecoverAfterRenewal(
        operation: String
    ) {
        val f = fixture()
        val id = begin(f, csv("MFA01"))
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Import assurance fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        for (permission in employeeImportPermissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                account,
                permission,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val list =
            ListEmployeeImports(
                imports,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val get =
            GetEmployeeImport(
                imports,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
                jobs,
            )
        val rows =
            GetEmployeeImportRows(
                imports,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val attempts =
            GetEmployeeImportAttempts(
                imports,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val actor =
            Actor(
                account,
                f.company,
                employeeImportPermissions,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val invoke: (Actor) -> Result<*> = { current ->
            when (operation) {
                "list" -> list.execute(current, null, 10)
                "summary" -> get.execute(current, id)
                "rows" -> rows.execute(current, id, null, 25)
                else -> attempts.execute(current, id, null, 10)
            }
        }
        val barrier = AccountLockProbe.Barrier(account)
        accountProbe.current.set(barrier)
        try {
            val expired =
                Executors.newSingleThreadExecutor().use { executor ->
                    val pending = executor.submit<Result<*>> { invoke(actor) }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(2))
                        barrier.release.countDown()
                        pending.get(10, TimeUnit.SECONDS)
                    } finally {
                        barrier.release.countDown()
                        accountProbe.current.set(null)
                    }
                }
            assertEquals("mfa_required", (expired as? Result.Failed)?.failure?.code)
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            assertTrue(invoke(renewed) is Result.Success)
            val originalWithoutRead =
                renewed.copy(permissions = renewed.permissions - "people.profile.read")
            assertEquals(
                "employee_import_access_required",
                (invoke(originalWithoutRead) as? Result.Failed)?.failure?.code,
            )
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select version from employee_imports where company_id=? and id=?",
                        Long::class.java,
                        f.company,
                        id,
                    ),
            )
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }
}
