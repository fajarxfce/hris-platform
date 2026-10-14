package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.math.BigDecimal
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PayrollAmendmentAssuranceTest : PayrollAmendmentApiFixture() {
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var sources: PayrollAmendmentSourceRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    private val security = IdentitySecurityPolicy()

    private data class Scenario(
        val fixture: AmendmentFixture,
        val actor: Actor,
        val observed: List<PayrollAmendmentSourceReference>,
        val remuneration: List<PayrollAmendmentRemuneration>,
    )

    private fun scenario(): Scenario {
        val fixture = amendmentFixture()
        val actor =
            payrollActor(fixture.payroll, fixture.maker).copy(mfaVerifiedAt = clock.instant())
        val prepared = getAmendmentPreparation.execute(actor, fixture.assessment)
        assertTrue(prepared is Result.Success, prepared.toString())
        val year = (prepared as Result.Success).value.sources
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        return Scenario(
            fixture,
            actor,
            year.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) },
            listOf(
                PayrollAmendmentRemuneration(
                    fixture.assessment,
                    year
                        .first()
                        .facts
                        .compensation
                        .copy(
                            basicSalary =
                                year.first().facts.compensation.basicSalary + BigDecimal("1000000")
                        ),
                )
            ),
        )
    }

    private fun invoke(scene: Scenario, action: String, actor: Actor = scene.actor): Result<*> =
        when (action) {
            "preparation" ->
                GetPayrollAmendmentPreparation(
                        policies,
                        sources,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.fixture.assessment)
            "preview" ->
                PreviewPayrollAmendment(
                        policies,
                        sources,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.fixture.assessment, scene.observed, scene.remuneration)
            else -> error("Unknown amendment action")
        }

    @ParameterizedTest
    @ValueSource(strings = ["preparation", "preview"])
    fun proofExpiryDuringAnAccessWaitCannotReleaseAnAmendmentReview(action: String) {
        val scene = scenario()
        val age =
            if (action == "preview") security.recentAuthenticationAge else security.maximumMfaAge
        val actor = scene.actor.copy(mfaVerifiedAt = clock.instant().minus(age).plusSeconds(1))
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene, action, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    if (action == "preview") "recent_authentication_required" else "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertTrue(
            invoke(scene, action, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["preparation", "preview"])
    fun acquiredFactsAndCalculationsStayPrivateWhenAssuranceExpiresBeforeDelivery(action: String) {
        val scene = scenario()
        val age =
            if (action == "preview") security.recentAuthenticationAge else security.maximumMfaAge
        val actor = scene.actor.copy(mfaVerifiedAt = clock.instant().minus(age).plusSeconds(1))
        amendmentProbe.afterRead = { company ->
            if (company == actor.companyId) clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals(
            if (action == "preview") "recent_authentication_required" else "mfa_required",
            (invoke(scene, action, actor) as Result.Failed).failure.code,
        )
        amendmentProbe.clear()
        assertTrue(
            invoke(scene, action, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @Test
    fun previewAlsoRetainsRecentPasswordAuthenticationThroughTheCalculation() {
        val scene = scenario()
        val actor =
            scene.actor.copy(
                authenticatedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        amendmentProbe.afterRead = { company ->
            if (company == actor.companyId) clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals(
            "recent_authentication_required",
            (invoke(scene, "preview", actor) as Result.Failed).failure.code,
        )
        amendmentProbe.clear()
        assertTrue(
            invoke(scene, "preview", actor.copy(authenticatedAt = clock.instant()))
                is Result.Success
        )
    }

    @ParameterizedTest
    @CsvSource("preparation,access", "preview,access", "preparation,sources", "preview,sources")
    fun interruptedPendingWorkReleasesItsGuardsAndTheExecutorThreadRemainsReusable(
        action: String,
        stage: String,
    ) {
        val scene = scenario()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        if (stage == "access")
            accountProbe.current.set(
                AccountLockProbe.Barrier(scene.actor.accountId, entered, release)
            )
        else
            amendmentProbe.afterRead = { company ->
                if (company == scene.actor.companyId) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
            }
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    owner.set(Thread.currentThread())
                    invoke(scene, action)
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val error =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertInstanceOf(InterruptedException::class.java, error.cause)
                accountProbe.current.set(null)
                amendmentProbe.clear()
                val next =
                    pool
                        .submit<Result<*>> {
                            assertFalse(Thread.currentThread().isInterrupted)
                            invoke(scene, action)
                        }
                        .get(10, TimeUnit.SECONDS)
                assertTrue(next is Result.Success, next.toString())
            } finally {
                release.countDown()
                accountProbe.current.set(null)
                amendmentProbe.clear()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["preparation", "preview"])
    fun permissionWithdrawnWhileWaitingIsCheckedBeforeAcquiringPrivateFacts(action: String) {
        val scene = scenario()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val armed = java.util.concurrent.atomic.AtomicBoolean(true)
        var acquired = false
        payrollProbe.beforePolicyLock = { company, shared ->
            if (company == scene.actor.companyId && shared && armed.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        amendmentProbe.afterRead = { company ->
            if (company == scene.actor.companyId) acquired = true
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene, action) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.amend'",
                        scene.actor.companyId,
                        scene.actor.accountId,
                    )
                release.countDown()
                assertEquals(
                    "access_denied",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
                assertFalse(acquired)
            } finally {
                release.countDown()
                payrollProbe.clear()
                amendmentProbe.clear()
            }
        }
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.amend')",
                scene.actor.companyId,
                scene.actor.accountId,
            )
        assertTrue(invoke(scene, action) is Result.Success)
        assertEquals(
            "access_denied",
            (invoke(scene, action, scene.actor.copy(permissions = setOf("company.read")))
                    as Result.Failed)
                .failure
                .code,
        )
    }
}
