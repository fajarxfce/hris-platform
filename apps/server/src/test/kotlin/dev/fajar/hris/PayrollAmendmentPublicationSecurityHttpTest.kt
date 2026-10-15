package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PayrollAmendmentPublicationSecurityHttpTest : PayrollAmendmentPublicationApiFixture() {
    @Test
    fun publicationRequiresItsExplicitGrantAndExcludesTheBeneficiary() {
        val scene = publicationScene()
        val f = scene.source.payroll
        payrollError(admitPublication(scene, member = scene.source.maker), 403, "access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.amend.publish')",
                f.company,
                f.owner.account,
            )
        payrollError(
            admitPublication(scene, member = f.owner),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
        assertEquals(0, count(f.company, "payroll_amendment_publications"))
        payrollBody(admitPublication(scene))
        payrollError(
            get(f.owner.client, publicationPath(scene)),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
    }

    @Test
    fun accountBindingAfterAdmissionExcludesTheNewBeneficiaryFromWorkerPublication() {
        val payroll = payrollFixture(bindAccount = false)
        val source = amendmentFixture(calculationFixture(f = processingFixture(payroll)))
        val scene = publicationScene(source)
        val lease = publicationLease(scene)
        payrollBody(
            command(
                payroll.admin,
                "/api/v1/companies/${payroll.company}/employees/${payroll.employee}/account-link",
                json.writeValueAsString(
                    mapOf(
                        "accountId" to scene.publisher.account,
                        "expectedVersion" to 0,
                        "reason" to "Initial verified employee account",
                    )
                ),
                payroll.adminCsrf,
                UUID.randomUUID(),
            )
        )
        val result = stepPublication(scene, lease) as Result.Failed
        assertEquals("payroll_amendment_self_preparation_forbidden", result.failure.code)
        assertEquals(Result.Success(Unit), abortAmendment.execute(lease, result.failure))
        assertEquals(1, count(payroll.company, "payroll_assessment_revisions"))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun currentPublicationGrantIsRequiredAfterWaitsIncludingOriginalReceiptReplay(replay: Boolean) {
        val scene = publicationScene()
        if (replay) payrollBody(admitPublication(scene))
        val before = publicationCounts(scene)
        val barrier = AccountLockProbe.Barrier(scene.publisher.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { admitPublication(scene) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.amend.publish'",
                        scene.source.payroll.company,
                        scene.publisher.account,
                    )
                barrier.release.countDown()
                payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(before, publicationCounts(scene))
    }

    @ParameterizedTest
    @CsvSource(
        "credential,session_revoked",
        "permission,access_denied",
        "membership,company_access_denied",
        "authentication,recent_authentication_required",
    )
    fun revokedOrExpiredWorkerAuthorityCannotPublishButItsOwnedCleanupStillCompletes(
        change: String,
        code: String,
    ) {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        val before = publicationCounts(scene)
        val barrier = AccountLockProbe.Barrier(scene.publisher.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { stepPublication(scene, lease) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                scene.publisher.account,
                            )
                    "permission" ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.amend.publish'",
                                scene.source.payroll.company,
                                scene.publisher.account,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                scene.source.payroll.company,
                                scene.publisher.account,
                            )
                    else -> clock.set(clock.instant().plusSeconds(601))
                }
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS) as Result.Failed
                assertEquals(code, result.failure.code)
                assertEquals(before, publicationCounts(scene))
                assertEquals(Result.Success(Unit), abortAmendment.execute(lease, result.failure))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, currentPayslip(scene)["summary"]["version"].asLong())
        assertEquals(
            "APPROVED",
            draftView(scene.review.draft)["amendment"]["summary"]["status"].asString(),
        )
    }

    @Test
    fun aRefreshedActorCannotReplaceTheInitiatingAuthenticationProof() {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        val before = publicationCounts(scene)
        val fresh =
            payrollActor(scene.source.payroll, scene.publisher)
                .copy(authenticatedAt = lease.job.request.authenticatedAt.plusSeconds(1))
        clock.set(clock.instant().plusSeconds(2))
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch")),
            publishAmendment.execute(fresh, lease),
        )
        val forged =
            lease.copy(
                job =
                    lease.job.copy(
                        request = lease.job.request.copy(authenticatedAt = fresh.authenticatedAt)
                    )
            )
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch")),
            publishAmendment.execute(fresh, forged),
        )
        assertEquals(before, publicationCounts(scene))
        assertEquals(Result.Success(JobStep(1, true)), stepPublication(scene, lease))
    }

    @Test
    fun authenticationExpiryAfterJournalingRollsBackThePublishedSuffix() {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        val before = publicationCounts(scene)
        payrollProbe.afterJournal = {
            if (it.action == "payroll.amendment_published")
                clock.set(clock.instant().plusSeconds(601))
        }
        val result = stepPublication(scene, lease) as Result.Failed
        payrollProbe.clear()
        assertEquals("recent_authentication_required", result.failure.code)
        assertEquals(before, publicationCounts(scene))
        assertEquals(0, currentPayslip(scene)["summary"]["version"].asLong())
        assertEquals(Result.Success(Unit), abortAmendment.execute(lease, result.failure))
    }

    @Test
    fun storageRejectsPublicationWithForgedReviewVersionActorOrJobIdentity() {
        val scene = publicationScene()
        val before = publicationCounts(scene)
        for (corruption in listOf("review", "approval", "actor", "job", "calculation")) {
            publicationProbe.transformPublication = { row ->
                when (corruption) {
                    "review" -> row.reviewVersion += 1
                    "approval" -> row.approvalVersion += 1
                    "actor" -> row.actorId = scene.source.maker.account
                    "job" -> row.jobId = UUID.randomUUID()
                    else -> row.calculationId = UUID.randomUUID()
                }
            }
            val failed = admitPublication(scene)
            assertTrue(failed.statusCode() in setOf(403, 409), "$corruption: ${failed.body()}")
            publicationProbe.clear()
            assertEquals(before, publicationCounts(scene))
        }
        publish(scene)
    }
}
