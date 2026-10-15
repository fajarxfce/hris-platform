package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollPaymentRepository
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.math.BigDecimal
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PayrollPaymentRecoveryReviewAssuranceTest : PayrollAmendmentPublicationApiFixture() {
    @Autowired private lateinit var payments: PayrollPaymentRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    private fun reader(
        source: PayrollPaymentRepository = payments,
        security: IdentitySecurityPolicy = IdentitySecurityPolicy(enforceMfa = false),
    ) =
        GetPayrollPaymentRecoveryReview(
            source,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun finalAcquisitionRechecksRecentAssuranceBeforeReturningFinanceEvidence(mfa: Boolean) {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val security = IdentitySecurityPolicy(enforceMfa = mfa)
        val proof = clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
        val actor =
            payrollActor(scene.source.payroll, bank.checker)
                .copy(authenticatedAt = proof, mfaVerifiedAt = proof.takeIf { mfa })
        if (mfa)
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    actor.accountId,
                )
        val before = publicationCounts(scene)
        val late =
            object : PayrollPaymentRepository by payments {
                override fun recoveries(companyId: UUID, assessmentId: UUID) =
                    payments.recoveries(companyId, assessmentId).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val failed = reader(late, security).execute(actor, scene.source.assessment)
        assertEquals("recent_authentication_required", (failed as Result.Failed).failure.code)
        assertEquals(before, publicationCounts(scene))
        val renewed =
            actor.copy(
                authenticatedAt = clock.instant(),
                mfaVerifiedAt = clock.instant().takeIf { mfa },
            )
        assertTrue(
            reader(security = security).execute(renewed, scene.source.assessment) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "membership", "credential"])
    fun revokedAccessAfterAnAccountWaitDoesNotReleaseTheReview(mode: String) {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val barrier = AccountLockProbe.Barrier(bank.checker.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    get(bank.checker.client, amendmentPath(scene.source) + "/recoveries/review")
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (mode) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                bank.checker.account,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                scene.source.payroll.company,
                                bank.checker.account,
                            )
                    else ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.pay'",
                                scene.source.payroll.company,
                                bank.checker.account,
                            )
                }
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals(if (mode == "credential") 401 else 403, result.statusCode())
                assertFalse(json.readTree(result.body()).has("payments"))
                assertFalse(json.readTree(result.body()).has("recoveries"))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun sharedReviewCannotMixAnOldBalanceWithANewReturnWhileTheWriterWaits() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val actor = payrollActor(scene.source.payroll, bank.checker)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)
        val held =
            object : PayrollPaymentRepository by payments {
                override fun recoveries(
                    companyId: UUID,
                    assessmentId: UUID,
                ): Result<List<PayrollPaymentRecovery>> {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return payments.recoveries(companyId, assessmentId)
                }
            }
        val observedWriter =
            object : PayrollPaymentRepository by payments {
                override fun lock(companyId: UUID, shared: Boolean): Result<Unit> {
                    if (!shared) writerEntered.countDown()
                    return payments.lock(companyId, shared)
                }
            }
        val write =
            ConfirmPayrollPaymentRecovery(
                observedWriter,
                people,
                companies,
                members,
                identities,
                transactions,
                IdentitySecurityPolicy(enforceMfa = false),
                clock,
                operations,
                journal,
            )
        Executors.newFixedThreadPool(3).use { pool ->
            val reading =
                pool.submit<Result<PayrollPaymentRecoveryReview>> {
                    reader(held).execute(actor, scene.source.assessment)
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val concurrent =
                    pool
                        .submit<Result<PayrollPaymentRecoveryReview>> {
                            reader().execute(actor, scene.source.assessment)
                        }
                        .get(3, TimeUnit.SECONDS)
                assertTrue(concurrent is Result.Success)
                val id = UUID.randomUUID()
                val writing =
                    pool.submit<Result<MutationReceipt>> {
                        write.execute(
                            actor,
                            UUID.randomUUID(),
                            scene.source.assessment,
                            id,
                            1,
                            4,
                            BigDecimal("100.00"),
                            "RETURN:$id",
                            clock.instant(),
                            "Confirmed return",
                        )
                    }
                assertTrue(writerEntered.await(5, TimeUnit.SECONDS))
                assertThrows(TimeoutException::class.java) {
                    writing.get(200, TimeUnit.MILLISECONDS)
                }
                release.countDown()
                val before = reading.get(10, TimeUnit.SECONDS)
                assertEquals(concurrent, before)
                assertTrue((before as Result.Success).value.recoveries.isEmpty())
                assertEquals(4, before.value.payments.version)
                assertTrue(writing.get(10, TimeUnit.SECONDS) is Result.Success)
                val after = reader().execute(actor, scene.source.assessment) as Result.Success
                assertEquals(5, after.value.payments.version)
                assertEquals(1, after.value.recoveries.size)
                assertEquals(
                    BigDecimal("100.00"),
                    after.value.payments.settlement.confirmedRecovered,
                )
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun interruptedAccountWaitReleasesResourcesAndTheExecutorCanReadAgain() {
        val scene = publicationScene()
        val bank = bankInstruction(scene)
        val actor = payrollActor(scene.source.payroll, bank.checker)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        val thread = AtomicReference<Thread>()
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<PayrollPaymentRecoveryReview>> {
                    thread.set(Thread.currentThread())
                    reader().execute(actor, scene.source.assessment)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                thread.get().interrupt()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertTrue(
                    generateSequence(failure as Throwable?) { it.cause }
                        .any { it is InterruptedException }
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            val retried =
                pool
                    .submit<Result<PayrollPaymentRecoveryReview>> {
                        reader().execute(actor, scene.source.assessment)
                    }
                    .get(10, TimeUnit.SECONDS)
            assertTrue(retried is Result.Success)
        }
    }
}
