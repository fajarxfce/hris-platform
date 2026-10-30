package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.organization.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class OrganizationCompletionTest : PeopleApiFixture() {
    @Autowired private lateinit var units: OrganizationRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "company_read",
                "root_read",
                "parent_read",
                "list_read",
                "company_create",
                "company_update",
                "unit_save",
                "company_create_receipt",
                "company_update_receipt",
            ]
    )
    fun organizationAcquisitionAndCompletionCannotOutliveItsMfaProof(mode: String) {
        val browser = client()
        val csrf = login(browser, "admin@example.test")
        val company = company(browser, csrf)
        val parent = UUID.randomUUID()
        val child = UUID.randomUUID()
        for ((id, parentId, code) in
            listOf(Triple(parent, null, "ROOT"), Triple(child, parent, "CHILD"))) {
            val response =
                command(
                    browser,
                    "/api/v1/companies/$company/organization-units/$id",
                    json.writeValueAsString(
                        mapOf(
                            "code" to code,
                            "name" to code,
                            "kind" to "DEPARTMENT",
                            "parentId" to parentId,
                            "active" to true,
                        )
                    ),
                    csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            assertEquals(200, response.statusCode(), response.body())
        }
        val author = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Organization assurance fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                author,
                "$author@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                author,
            )
        for (permission in setOf("company.read", "company.manage")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                author,
                permission,
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) values(?,'companies.create')",
                author,
            )
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val creating = mode.startsWith("company_create")
        val actor =
            Actor(
                author,
                if (creating) null else company,
                if (creating) setOf("companies.create")
                else setOf("company.read", "company.manage"),
                original,
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1),
            )
        var expire = false
        var boundaries = 0
        fun boundary() {
            boundaries++
            if (expire) clock.set(original.plusSeconds(2))
        }
        val observedCompanies =
            object : CompanyRepository by companies {
                override fun find(id: UUID) =
                    companies.find(id).also { if (mode == "company_read") boundary() }

                override fun create(actor: Actor, operationId: UUID, company: Company) =
                    companies.create(actor, operationId, company).also {
                        if (mode == "company_create_receipt") boundary()
                    }

                override fun update(actor: Actor, operationId: UUID, company: Company) =
                    companies.update(actor, operationId, company).also {
                        if (mode == "company_update_receipt") boundary()
                    }
            }
        val observedUnits =
            object : OrganizationRepository by units {
                override fun find(companyId: UUID, id: UUID) =
                    units.find(companyId, id).also {
                        if (id == parent && mode in setOf("root_read", "parent_read")) boundary()
                    }

                override fun list(companyId: UUID, search: OrganizationUnitSearch) =
                    units.list(companyId, search).also { if (mode == "list_read") boundary() }
            }
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        if (mode in setOf("company_create", "company_update", "unit_save"))
                            boundary()
                    }
            }
        val key = UUID.randomUUID()
        val newCompany = UUID.nameUUIDFromBytes("company:$author:$key".toByteArray())
        val code = "C${key.toString().take(8)}"
        val newUnit =
            UnitChange(
                UUID.randomUUID(),
                "NEW_UNIT",
                "New unit",
                UnitKind.DEPARTMENT,
                parent,
                null,
                true,
                null,
            )
        fun invoke(selected: Actor): Result<*> =
            when (mode) {
                "company_read" ->
                    GetCompany(
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected)
                "root_read",
                "parent_read" ->
                    GetOrganizationUnit(
                            observedUnits,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, if (mode == "root_read") parent else child)
                "list_read" ->
                    ListOrganizationUnits(
                            observedUnits,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, OrganizationUnitSearch(limit = 20))
                "company_create",
                "company_create_receipt" ->
                    CreateCompany(
                            observedCompanies,
                            identities,
                            transactions,
                            observedJournal,
                            security,
                            clock,
                        )
                        .execute(selected, key, code, "New company", "Asia/Jakarta")
                "company_update",
                "company_update_receipt" ->
                    UpdateCompany(
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            observedJournal,
                            security,
                            clock,
                        )
                        .execute(selected, key, code, "Updated company", "Asia/Makassar", 0)
                else ->
                    SaveOrganizationUnit(
                            observedUnits,
                            companies,
                            members,
                            identities,
                            operations,
                            observedJournal,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, key, newUnit)
            }
        fun state() =
            buildMap<String, Any?> {
                for (table in
                    listOf(
                        "audit_entries",
                        "outbox_events",
                        "operation_receipts",
                        "company_memberships",
                    )) put(
                    table,
                    database()
                        .queryForObject(
                            "select count(*) from $table where company_id in (?,?)",
                            Long::class.java,
                            company,
                            newCompany,
                        ),
                )
                put(
                    "companies",
                    database()
                        .queryForList(
                            "select id,version,code,name,timezone from companies where id in (?,?) order by id",
                            company,
                            newCompany,
                        ),
                )
                put(
                    "units",
                    database()
                        .queryForList(
                            "select id,version,code,name,parent_id from organization_units where company_id=? order by id",
                            company,
                        ),
                )
            }
        try {
            val read = mode.endsWith("_read")
            val replay = mode.endsWith("_receipt")
            val normal = if (read || replay) invoke(actor) else null
            if (normal != null) assertTrue(normal is Result.Success, normal.toString())
            val before = state()
            expire = true
            boundaries = 0
            val denied = invoke(actor)
            assertEquals(1, boundaries, "The real final boundary must be reached")
            assertEquals("mfa_required", (denied as? Result.Failed)?.failure?.code)
            assertEquals(before, state())
            expire = false
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val accepted = invoke(renewed)
            assertTrue(accepted is Result.Success, accepted.toString())
            if (read) assertEquals(normal, accepted)
            if (replay)
                assertEquals(
                    ((normal as Result.Success).value as MutationReceipt).copy(replayed = true),
                    (accepted as Result.Success).value,
                )
            if (!read) {
                val committed = state()
                assertEquals(
                    Result.Success(
                        ((accepted as Result.Success).value as MutationReceipt).copy(
                            replayed = true
                        )
                    ),
                    invoke(renewed),
                )
                assertEquals(committed, state())
            }
        } finally {
            clock.set(original)
        }
    }
}
