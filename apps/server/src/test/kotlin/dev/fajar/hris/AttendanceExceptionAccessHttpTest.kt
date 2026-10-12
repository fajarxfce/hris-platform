package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import dev.fajar.hris.workforce.domain.usecases.ListAttendanceExceptions
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AttendanceReadProbeConfiguration::class)
class AttendanceExceptionAccessHttpTest : AttendanceExceptionApiFixture() {
    @Autowired private lateinit var read: ListAttendanceExceptions
    @Autowired private lateinit var readProbe: AttendanceReadProbe
    @Autowired private lateinit var attendance: AttendanceRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private fun invoke(actor: Actor, reader: ListAttendanceExceptions = read) =
        reader.execute(
            actor,
            LocalDate.parse("2026-09-01"),
            LocalDate.parse("2026-10-01"),
            null,
            null,
            50,
        )

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credentials"])
    fun currentAuthorityIsRecheckedAfterAccessWaits(mode: String) {
        val f = isolatedFixture()
        recordOfflineEvent(f)
        val response =
            waiting(
                f.actor.accountId,
                {
                    when (mode) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.read'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                    }
                },
                { get(f.admin, queuePath(f)) },
            )
        assertTrue(response.statusCode() in listOf(401, 403), response.body())
        assertFalse(response.body().contains("Example Employee"))
        assertFalse(response.body().contains("pendingCount"))
    }

    @Test
    fun originalGrantsCannotExpandToACompanyWideDirectory() {
        val f = isolatedFixture()
        recordOfflineEvent(f)
        val result = invoke(f.actor.copy(permissions = setOf("attendance.verify")))
        assertEquals("access_denied", (result as Result.Failed).failure.code)
    }

    @ParameterizedTest
    @ValueSource(strings = ["account", "projection"])
    fun proofExpiryDuringAReadCannotReleaseItsRows(stage: String) {
        val f = isolatedFixture()
        recordOfflineEvent(f)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        // The HTTP fixture disables MFA for account setup; this reader enforces production policy.
        val security = IdentitySecurityPolicy()
        val reader =
            ListAttendanceExceptions(
                attendance,
                people,
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
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        if (stage == "account")
            accountProbe.current.set(AccountLockProbe.Barrier(f.actor.accountId, entered, release))
        else readProbe.current.set(AttendanceReadProbe.Barrier(f.company, entered, release))
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor, reader) }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    release.countDown()
                    accountProbe.current.set(null)
                    readProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
        val recovered = invoke(actor.copy(mfaVerifiedAt = clock.instant()), reader)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(1, (recovered as Result.Success).value.items.size)
    }

    @Test
    fun cancellingAPendingProjectionReleasesOwnershipAndCannotPoisonTheNextRead() {
        val f = isolatedFixture()
        recordOfflineEvent(f)
        val barrier = AttendanceReadProbe.Barrier(f.company)
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<Result<*>> { invoke(f.actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(pending.isCancelled)
                barrier.release.countDown()
                val recovered = pool.submit<Result<*>> { invoke(f.actor) }.get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
        assertEquals(1, exceptionPage(f)["items"].size())
    }

    @Test
    fun aCoherentDirectorySnapshotDoesNotBlockAnIndependentAttendanceReview() {
        val f = isolatedFixture()
        val event = recordOfflineEvent(f)
        val barrier = AttendanceReadProbe.Barrier(f.company)
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.admin, queuePath(f)) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                pool.submit { reviewEvent(f, event, "REJECT") }.get(4, TimeUnit.SECONDS)
                barrier.release.countDown()
                val earlier = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, earlier.statusCode(), earlier.body())
                assertEquals(1, json.readTree(earlier.body())["items"][0]["pendingCount"].asInt())
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
        assertTrue(exceptionPage(f)["items"].isEmpty)
    }
}
