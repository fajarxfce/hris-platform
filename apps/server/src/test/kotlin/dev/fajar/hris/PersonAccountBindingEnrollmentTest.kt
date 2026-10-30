package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.BindPersonAccount
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

class PersonAccountBindingEnrollmentTest : PeopleApiFixture() {
    @Autowired private lateinit var profiles: PersonProfileRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var accounts: AccountAdministrationRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @CsvSource("false,true", "true,true", "false,false")
    fun currentEnrollmentIsRequiredForBindingAndItsReceipt(replay: Boolean, mfa: Boolean) {
        val browser = client()
        val csrf = login(browser, "admin@example.test")
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        fun account(): UUID {
            val id = UUID.randomUUID()
            database()
                .update(
                    "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Binding enrollment fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                    id,
                    "$id@example.test",
                )
            database()
                .update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    company,
                    id,
                )
            for (permission in PermissionCatalog.companyAdministrator) database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    id,
                    permission,
                )
            return id
        }
        val author = account()
        val target = account()
        val original = clock.instant()
        val actor =
            Actor(
                author,
                company,
                PermissionCatalog.companyAdministrator,
                original,
                UUID.randomUUID(),
                mfaVerifiedAt = original,
                credentialVersion = 0,
            )
        val subject =
            BindPersonAccount(
                profiles,
                people,
                companies,
                identities,
                accounts,
                members,
                operations,
                journal,
                transactions,
                IdentitySecurityPolicy(enforceMfa = mfa),
                clock,
            )
        val key = UUID.randomUUID()
        fun invoke() = subject.execute(actor, key, employee, target, 0, "Verified identity")
        fun state() =
            buildMap<String, Any?> {
                for (table in listOf("audit_entries", "outbox_events", "operation_receipts")) put(
                    table,
                    database()
                        .queryForObject(
                            "select count(*) from $table where company_id=?",
                            Long::class.java,
                            company,
                        ),
                )
                put(
                    "profile",
                    database()
                        .queryForList(
                            "select p.id,p.version,p.account_id from persons p join employments e on e.person_id=p.id where e.company_id=? and e.id=?",
                            company,
                            employee,
                        ),
                )
            }
        val first = if (replay) invoke() else null
        if (first != null) assertTrue(first is Result.Success, first.toString())
        // Explicit stored-enrollment fixture; keep the observed credential version unchanged.
        database().update("update accounts set mfa_secret_encrypted=null where id=?", author)
        val before = state()
        val denied = invoke()
        if (!mfa) {
            assertTrue(denied is Result.Success, denied.toString())
            val committed = state()
            assertEquals(
                Result.Success((denied as Result.Success).value.copy(replayed = true)),
                invoke(),
            )
            assertEquals(committed, state())
            return
        }
        assertEquals("mfa_setup_required", (denied as? Result.Failed)?.failure?.code)
        assertEquals(before, state())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                author,
            )
        val completed = invoke()
        assertTrue(completed is Result.Success, completed.toString())
        if (first != null)
            assertEquals(
                Result.Success((first as Result.Success).value.copy(replayed = true)),
                completed,
            )
        val committed = state()
        assertEquals(
            Result.Success((completed as Result.Success).value.copy(replayed = true)),
            invoke(),
        )
        assertEquals(committed, state())
    }
}
