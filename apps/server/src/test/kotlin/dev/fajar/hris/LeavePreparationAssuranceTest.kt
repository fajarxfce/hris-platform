package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(LeavePreparationProbeConfiguration::class)
class LeavePreparationAssuranceTest : LeaveAttachmentApiFixture() {
    @Autowired private lateinit var carry: LeaveCarryRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var documents: DocumentRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var requests: LeaveRequestRepository
    @Autowired private lateinit var ledger: LeaveLedgerRepository
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    @Autowired private lateinit var reads: LeavePreparationProbe
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    @AfterEach
    fun releasePreparationProbe() {
        reads.current.getAndSet(null)?.release?.countDown()
    }

    @ParameterizedTest
    @CsvSource(
        "employees,false",
        "employees,true",
        "evidence,false",
        "evidence,true",
        "preview,false",
        "preview,true",
    )
    fun expiredAssuranceCannotPublishPreparationAfterWaitingOrAcquisition(
        kind: String,
        acquired: Boolean,
    ) {
        val scene = scene()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val account = AccountLockProbe.Barrier(actor.accountId)
        val data = LeavePreparationProbe.Barrier(scene.fixture.company, kind)
        if (acquired) reads.current.set(data) else accountProbe.current.set(account)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, scene, actor) }
            try {
                assertTrue(
                    (if (acquired) data.entered else account.entered).await(5, TimeUnit.SECONDS)
                )
                clock.set(clock.instant().plusSeconds(2))
                account.release.countDown()
                data.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                account.release.countDown()
                data.release.countDown()
                accountProbe.current.set(null)
                reads.current.set(null)
            }
        }
        assertTrue(read(kind, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["employees", "evidence", "preview"])
    fun preparationRequiresBothOriginalAndCurrentManagementPermission(kind: String) {
        val scene = scene()
        assertEquals(
            "access_denied",
            (read(kind, scene, scene.actor.copy(permissions = emptySet())) as Result.Failed)
                .failure
                .code,
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.manage'",
                scene.fixture.company,
                scene.actor.accountId,
            )
        assertEquals(
            "access_denied",
            (read(kind, scene, scene.actor) as Result.Failed).failure.code,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["employees", "evidence", "preview"])
    fun cancellationReleasesResourceAndAccessGuardsForSubsequentWork(kind: String) {
        val scene = scene()
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, scene, scene.actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertThrows(CancellationException::class.java) {
                    pending.get(10, TimeUnit.SECONDS)
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            assertTrue(
                pool.submit<Result<*>> { read(kind, scene, scene.actor) }.get(10, TimeUnit.SECONDS)
                    is Result.Success
            )
            val released =
                pool
                    .submit<Result<Unit>> {
                        transactions.run(scene.actor) {
                            val company = scene.fixture.company
                            ledger
                                .lock(company, scene.fixture.employee)
                                .flatMap { schedules.lock(company) }
                                .flatMap { cutoffs.lock(company) }
                                .flatMap { policies.lock(company) }
                                .flatMap { people.lockReportingLines(company) }
                                .flatMap { documents.lock(company) }
                                .flatMap { companies.lock(company) }
                                .flatMap { members.lock(company) }
                                .flatMap { identities.lockAccount(scene.actor.accountId) }
                        }
                    }
                    .get(10, TimeUnit.SECONDS)
            assertTrue(released is Result.Success, released.toString())
        }
        assertEquals("0", balance(scene.fixture)["reservedDays"].asString())
    }

    @Test
    fun sharedPreviewAlsoRejectsSelfAssuranceThatExpiresDuringBalanceRead() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val actor =
            actor(f)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val scene = Scene(f, actor)
        val barrier = LeavePreparationProbe.Barrier(f.company, "preview")
        reads.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> { read("preview", scene, actor, LeavePreparationScope.SELF) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                reads.current.set(null)
                barrier.release.countDown()
            }
        }
    }

    private data class Scene(val fixture: LeaveFixture, val actor: Actor)

    private fun scene(): Scene {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        val (account, _) = member(f, listOf("leave.manage"))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        return Scene(
            f,
            Actor(
                account,
                f.company,
                setOf("leave.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant(),
            ),
        )
    }

    private fun read(
        kind: String,
        scene: Scene,
        actor: Actor,
        scope: LeavePreparationScope = LeavePreparationScope.ADMINISTRATIVE,
    ): Result<*> =
        when (kind) {
            "employees" ->
                ListLeavePreparationEmployees(
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, "", null, 20)
            "evidence" ->
                ListLeavePreparationEvidence(
                        documents,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, scene.fixture.employee, null, 20)
            "preview" ->
                PreviewLeaveRequest(
                        requests,
                        ledger,
                        policies,
                        people,
                        companies,
                        schedules,
                        identities,
                        members,
                        transactions,
                        clock,
                        cutoffs,
                        security,
                        carry,
                    )
                    .execute(
                        actor,
                        scene.fixture.employee,
                        scene.fixture.type,
                        listOf(RequestedLeaveDay(LocalDate.parse("2026-10-05"), LeavePortion.FULL)),
                        scope,
                    )
            else -> error("Unknown preparation read")
        }
}
