package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveCarryExpiryReadAssuranceTest : LeaveCarryStorageFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var batches: LeaveBatchRepository
    @Autowired private lateinit var resumeExpiry: ResumeLeaveCarryExpiry
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    private fun origin(c: PreparedCarry): LeaveCarryExpiry =
        success(
                transactions.run(accountingActor(c.f)) { carryExpiries.list(c.f.company, null, 10) }
            )
            .items
            .single()

    private fun reader(c: PreparedCarry): Actor {
        val actor = accountingActor(c.f)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        return actor.copy(permissions = setOf("leave.read"), mfaVerifiedAt = clock.instant())
    }

    private fun read(
        kind: String,
        actor: Actor,
        id: UUID,
        repository: LeaveCarryExpiryRepository = carryExpiries,
    ): Result<*> =
        if (kind == "list")
            ListLeaveCarryExpiries(
                    repository,
                    companies,
                    members,
                    identities,
                    transactions,
                    clock,
                    security,
                )
                .execute(actor, null, 50)
        else
            GetLeaveCarryExpiry(
                    repository,
                    carryExpiryJobs,
                    batches,
                    people,
                    companies,
                    members,
                    identities,
                    transactions,
                    clock,
                    security,
                )
                .execute(actor, id, null, 50)

    @ParameterizedTest
    @CsvSource("list,false", "list,true", "detail,false", "detail,true")
    fun proofIsRecheckedAfterTheGuardWaitAndPrivateAcquisition(kind: String, acquired: Boolean) {
        val c = carried()
        val id = origin(c).id
        val actor =
            reader(c)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val account = AccountLockProbe.Barrier(actor.accountId)
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val guarded =
            object : LeaveCarryExpiryRepository by carryExpiries {
                override fun overview(
                    companyId: UUID,
                    id: UUID,
                ): Result<LeaveCarryExpiryOverview?> {
                    val value = carryExpiries.overview(companyId, id)
                    entered.countDown()
                    check(released.await(5, TimeUnit.SECONDS))
                    return value
                }

                override fun overviews(
                    companyId: UUID,
                    after: UUID?,
                    limit: Int,
                ): Result<Page<LeaveCarryExpiryOverview>> {
                    val value = carryExpiries.overviews(companyId, after, limit)
                    entered.countDown()
                    check(released.await(5, TimeUnit.SECONDS))
                    return value
                }
            }
        if (!acquired) accountProbe.current.set(account)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    read(kind, actor, id, if (acquired) guarded else carryExpiries)
                }
            try {
                assertTrue((if (acquired) entered else account.entered).await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                released.countDown()
                account.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                released.countDown()
                account.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertTrue(read(kind, actor.copy(mfaVerifiedAt = clock.instant()), id) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "detail"])
    fun currentCredentialRevocationWinsOverAnAdmittedRead(kind: String) {
        val c = carried()
        val id = origin(c).id
        val actor = reader(c)
        assertEquals(
            "access_denied",
            (read(kind, actor.copy(permissions = emptySet()), id) as Result.Failed).failure.code,
        )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, actor, id) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        actor.accountId,
                    )
                barrier.release.countDown()
                assertEquals(
                    "session_revoked",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "detail"])
    fun cancellationAfterAcquisitionEscapesAndReleasesTheTransaction(kind: String) {
        val c = carried()
        val id = origin(c).id
        val actor = reader(c)
        val interrupted =
            object : LeaveCarryExpiryRepository by carryExpiries {
                override fun overview(
                    companyId: UUID,
                    id: UUID,
                ): Result<LeaveCarryExpiryOverview?> {
                    success(carryExpiries.overview(companyId, id))
                    throw CancellationException("Cancelled fixture read")
                }

                override fun overviews(
                    companyId: UUID,
                    after: UUID?,
                    limit: Int,
                ): Result<Page<LeaveCarryExpiryOverview>> {
                    success(carryExpiries.overviews(companyId, after, limit))
                    throw CancellationException("Cancelled fixture read")
                }
            }
        assertThrows(CancellationException::class.java) { read(kind, actor, id, interrupted) }
        assertTrue(read(kind, actor, id) is Result.Success)
        assertEquals(1, accountingRows(c.f, "leave_carry_expiry_attempts"))
        assertEquals(0, accountingRows(c.f, "leave_carry_expiry_outcomes"))
    }

    @Test
    fun aConcurrentRecoveryCannotMixTheOldJobWithTheNewOrigin() {
        val c = carried()
        val original = origin(c)
        database()
            .update(
                "update background_jobs set status='FAILED',finished_at=now() where company_id=?",
                c.f.company,
            )
        val actor = reader(c)
        val observed = CountDownLatch(1)
        val released = CountDownLatch(1)
        val delayed =
            object : LeaveCarryExpiryRepository by carryExpiries {
                override fun find(
                    companyId: UUID,
                    id: UUID,
                    lock: Boolean,
                ): Result<LeaveCarryExpiry?> {
                    val value = carryExpiries.find(companyId, id, lock)
                    if (!lock) {
                        observed.countDown()
                        check(released.await(5, TimeUnit.SECONDS))
                    }
                    return value
                }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read("detail", actor, original.id, delayed) }
            try {
                assertTrue(observed.await(5, TimeUnit.SECONDS))
                success(
                    resumeExpiry.execute(
                        accountingActor(c.f).copy(mfaVerifiedAt = clock.instant()),
                        UUID.randomUUID(),
                        original.id,
                        0,
                        "Recover stopped work",
                    )
                )
                released.countDown()
                assertEquals(
                    "leave_carry_expiry_changed",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                released.countDown()
            }
        }
        val details = success(read("detail", actor, original.id)) as LeaveCarryExpiryDetails
        assertEquals(1, details.overview.expiry.version)
        assertNotEquals(original.jobId, details.job.request.id)
        assertEquals(details.overview.expiry.jobId, details.job.request.id)
        assertEquals(2, details.attempts.size)
    }
}
