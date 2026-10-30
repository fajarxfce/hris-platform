package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobKind
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.EmployeeImportRepository
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class EmployeeImportReadCompletionTest : EmployeeImportApiFixture() {
    @Autowired private lateinit var imports: EmployeeImportRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var jobs: JobRepository

    @ParameterizedTest
    @ValueSource(strings = ["details", "list", "rows", "attempts"])
    fun acquiredImportDataRequiresCurrentAssuranceThroughCompletion(mode: String) {
        val f = fixture()
        val id = begin(f, csv("E01"))
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Import read fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        for (permission in f.actor.permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                account,
                permission,
            )
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            f.actor.copy(
                accountId = account,
                credentialVersion = 0,
                mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1),
            )
        var expire = false
        var acquisitions = 0
        fun <T> acquired(value: Result<T>): Result<T> {
            assertTrue(value is Result.Success, value.toString())
            acquisitions++
            if (expire) clock.set(original.plusSeconds(2))
            return value
        }
        val observed =
            object : EmployeeImportRepository by imports {
                override fun list(companyId: UUID, after: UUID?, limit: Int) =
                    acquired(imports.list(companyId, after, limit))

                override fun rows(companyId: UUID, id: UUID, after: Int?, limit: Int) =
                    acquired(imports.rows(companyId, id, after, limit))

                override fun attempts(companyId: UUID, id: UUID, after: UUID?, limit: Int) =
                    acquired(imports.attempts(companyId, id, after, limit))
            }
        val observedJobs =
            object : JobRepository by jobs {
                override fun find(companyId: UUID, id: UUID, lock: Boolean) =
                    acquired(jobs.find(companyId, id, lock))
            }
        fun read(selected: Actor): Result<*> =
            when (mode) {
                "details" ->
                    GetEmployeeImport(
                            observed,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                            observedJobs,
                        )
                        .execute(selected, id)
                "list" ->
                    ListEmployeeImports(
                            observed,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, null, 20)
                "rows" ->
                    GetEmployeeImportRows(
                            observed,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, id, null, 20)
                else ->
                    GetEmployeeImportAttempts(
                            observed,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, id, null, 20)
            }
        fun counts() =
            listOf("audit_entries", "outbox_events", "operation_receipts", "employments")
                .associateWith {
                    database()
                        .queryForObject(
                            "select count(*) from $it where company_id=?",
                            Long::class.java,
                            f.company,
                        )
                }
        try {
            val before = counts()
            val normal = read(actor)
            assertTrue(normal is Result.Success, normal.toString())
            assertEquals(1, acquisitions)
            expire = true
            acquisitions = 0
            val denied = read(actor)
            assertEquals(1, acquisitions)
            assertEquals("mfa_required", (denied as? Result.Failed)?.failure?.code)
            assertEquals(before, counts())
            assertEquals(normal, read(actor.copy(mfaVerifiedAt = clock.instant())))
            assertEquals(before, counts())
        } finally {
            clock.set(original)
        }
    }
}
