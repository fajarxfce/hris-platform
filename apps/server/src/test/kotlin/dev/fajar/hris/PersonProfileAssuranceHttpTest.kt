package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class PersonProfileAssuranceHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var profiles: PersonProfileRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var accountProbe: AccountLockProbe

    @ParameterizedTest
    @ValueSource(strings = ["get", "history", "save"])
    fun expiredProofAfterWaitingCannotReadChangeOrReplayAPrivateProfile(operation: String) {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val person =
            database()
                .queryForObject(
                    "select person_id from employments where id=?",
                    UUID::class.java,
                    employee,
                )!!
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Profile assurance fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in listOf("people.profile.read", "people.profile.manage")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val get =
            GetPersonProfile(
                profiles,
                people,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val history =
            GetPersonProfileHistory(
                profiles,
                people,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val save =
            SavePersonProfile(
                profiles,
                people,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val actor =
            Actor(
                account,
                company,
                setOf("people.profile.read", "people.profile.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val key = UUID.randomUUID()
        val invoke = { current: Actor ->
            when (operation) {
                "get" -> get.execute(current, employee)
                "history" -> history.execute(current, employee, null, 50)
                else ->
                    save.execute(
                        current,
                        key,
                        employee,
                        0,
                        "Updated profile",
                        null,
                        "ID",
                        null,
                        "Verified correction",
                    )
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
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select version from persons where id=?",
                        Long::class.java,
                        person,
                    ),
            )
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val accepted = invoke(renewed)
            assertTrue(accepted is Result.Success)
            if (operation == "save") {
                val receipt = (accepted as Result.Success).value as MutationReceipt
                assertEquals(person, receipt.id)
                assertEquals(1L, receipt.version)
                assertEquals(Result.Success(receipt.copy(replayed = true)), invoke(renewed))
                clock.set(clock.instant().plus(security.maximumMfaAge).plusSeconds(1))
                assertEquals("mfa_required", (invoke(renewed) as? Result.Failed)?.failure?.code)
                assertEquals(
                    1L,
                    database()
                        .queryForObject(
                            "select version from persons where id=?",
                            Long::class.java,
                            person,
                        ),
                )
                assertEquals(
                    1,
                    database()
                        .queryForObject(
                            "select count(*) from operation_receipts where operation_id=?",
                            Int::class.java,
                            key,
                        ),
                )
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }
}
