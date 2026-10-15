package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentPublicationHttpTest : PayrollAmendmentPublicationApiFixture() {
    @Test
    fun publishedCorrectionRetainsOriginalEvidenceAndAdvancesEmployeeProjectionsTogether() {
        val scene = publicationScene()
        val original = currentPayslip(scene)
        val receipt = payrollBody(admitPublication(scene))
        assertEquals(4, receipt["version"].asLong())
        val lease = publicationLease(scene)
        assertEquals(Result.Success(JobStep(1, true)), stepPublication(scene, lease))
        val current = currentPayslip(scene)
        assertEquals(1, current["summary"]["version"].asLong())
        assertNotEquals(original["summary"]["takeHome"], current["summary"]["takeHome"])
        assertEquals(original, currentPayslip(scene, 0))
        val candidates =
            payrollBody(
                get(
                    scene.source.maker.client,
                    "/api/v1/companies/${scene.source.payroll.company}/payroll/amendment-sources?year=2026",
                )
            )["items"]
        assertEquals(1, candidates.size())
        assertEquals(scene.source.assessment.toString(), candidates[0]["assessmentId"].asString())
        assertEquals(1, candidates[0]["revision"].asLong())
        assertEquals(current["summary"]["publishedAt"], candidates[0]["publishedAt"])
        val progress = currentProgress(scene)
        assertEquals(1, progress["version"].asLong())
        assertEquals(1, progress["settlement"]["assessmentRevision"].asLong())
        assertEquals(
            current["summary"]["takeHome"].asString(),
            progress["settlement"]["entitlement"].asString(),
        )
        assertEquals("PUBLISHED", publicationView(scene)[0]["publication"]["status"].asString())
        assertEquals("SUCCEEDED", publicationView(scene)[0]["job"]["status"].asString())
        assertEquals(5, draftView(scene.review.draft)["amendment"]["summary"]["version"].asLong())
        assertEquals(receipt, payrollBody(admitPublication(scene)))
        assertEquals(2, count(scene.source.payroll.company, "payroll_assessment_revisions"))
        val changes =
            database()
                .queryForList(
                    "select collection,resource_version from mobile_sync_changes where company_id=? and resource_id=? order by collection,resource_version",
                    scene.source.payroll.company,
                    scene.source.assessment,
                )
        assertEquals(
            listOf(0L, 1L),
            changes.filter { it["collection"] == "PAYSLIPS" }.map { it["resource_version"] },
        )
        assertEquals(
            listOf(1L),
            changes.filter { it["collection"] == "PAYROLL_PAYMENTS" }.map { it["resource_version"] },
        )
        payrollError(
            get(scene.source.payroll.owner.client, amendmentPath(scene.source) + "?revision=2"),
            404,
            "payroll_payslip_not_found",
        )
    }

    @Test
    fun completeTwoMonthSuffixUsesItsExactCorrectedPredecessor() {
        val source = amendmentFixture()
        val next = followingMonth(source.publication.calculation, YearMonth.of(2026, 10))
        publishTaxRun(next, beginTaxRun(next))
        val fresh =
            source.copy(
                publication =
                    source.publication.copy(
                        calculation = source.publication.calculation.copy(people = next.people)
                    ),
                maker =
                    source.maker.copy(
                        csrf = login(source.maker.client, "${source.maker.account}@example.test")
                    ),
            )
        val scene = publicationScene(fresh)
        publish(scene)
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from payroll_assessment_heads where company_id=? and revision=1",
                    Int::class.java,
                    source.payroll.company,
                ),
        )
        val predecessor =
            database()
                .queryForObject(
                    "select previous_assessment_revision from payroll_assessment_evidence where company_id=? and revision=1 and assessment_id<>?",
                    Long::class.java,
                    source.payroll.company,
                    source.assessment,
                )
        assertEquals(1, predecessor)
        val originalNext = next.people.payroll.owner
        val nextAssessment =
            database()
                .queryForObject(
                    "select id from payroll_assessments where company_id=? and tax_month='2026-10-01'",
                    UUID::class.java,
                    source.payroll.company,
                )!!
        val read =
            payrollBody(
                get(
                    originalNext.client,
                    "/api/v1/companies/${source.payroll.company}/payroll/payslips/$nextAssessment",
                )
            )
        assertEquals(1, read["summary"]["version"].asLong())
    }

    @Test
    fun separateAmendmentsContinueFromCurrentEvidenceWithoutReplacingPreviousRevisions() {
        val first = publicationScene()
        publish(first)
        val prior = currentPayslip(first)
        val next = publicationScene(first.source, "500000", first.review)
        publish(next)
        assertEquals(2, currentPayslip(next)["summary"]["version"].asLong())
        assertEquals(prior, currentPayslip(next, 1))
        assertEquals(3, count(next.source.payroll.company, "payroll_assessment_revisions"))
        assertEquals(2, currentProgress(next)["version"].asLong())
    }

    @Test
    fun cancellationReturnsTheExactApprovedBasisAndAnOldLeaseCannotPublishIt() {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        val job = publicationView(scene)[0]["job"]
        payrollBody(
            command(
                scene.publisher.client,
                "/api/v1/companies/${scene.source.payroll.company}/jobs/${lease.job.request.id}/cancel",
                json.writeValueAsString(mapOf("expectedVersion" to job["version"].asLong())),
                scene.publisher.csrf,
                UUID.randomUUID(),
            )
        )
        assertEquals(
            Result.Success(Unit),
            abortAmendment.execute(lease, Failure(FailureKind.CONFLICT, "job_cancelled")),
        )
        assertEquals("STOPPED", publicationView(scene)[0]["publication"]["status"].asString())
        assertEquals("APPROVED", publicationView(scene)[0]["amendment"]["status"].asString())
        assertEquals(0, currentPayslip(scene)["summary"]["version"].asLong())
        assertTrue(stepPublication(scene, lease) is Result.Failed)
        val next = scene.copy(id = UUID.randomUUID(), operation = UUID.randomUUID())
        payrollBody(admitPublication(next, 5))
        val retry =
            claimPayrollJobs(JobKind.PAYROLL_AMENDMENT_PUBLISH).single {
                it.job.request.values["publicationId"] == next.id.toString()
            }
        assertEquals(Result.Success(JobStep(1, true)), stepPublication(next, retry))
        assertEquals(
            listOf("STOPPED", "PUBLISHED"),
            publicationView(next).let { rows ->
                (0 until rows.size()).map { rows[it]["publication"]["status"].asString() }
            },
        )
    }

    @Test
    fun terminalJobRequiresExplicitVersionedRecoveryBeforeAnotherAttempt() {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        assertEquals(
            Result.Success(true),
            transactions.run(payrollActor(scene.source.payroll, scene.publisher)) {
                publicationJobs.failExpired(lease, "job_attempt_limit")
            },
        )
        val view = publicationView(scene)[0]
        assertEquals("RECOVER", view["availableActions"][0].asString())
        val key = UUID.randomUUID()
        val body =
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to 4,
                    "expectedJobVersion" to view["job"]["version"].asLong(),
                    "reason" to "Recover exhausted worker",
                )
            )
        val receipt =
            payrollBody(
                command(
                    scene.publisher.client,
                    publicationPath(scene) + "/${scene.id}/recover",
                    body,
                    scene.publisher.csrf,
                    key,
                )
            )
        assertEquals(5, receipt["version"].asLong())
        assertEquals(
            receipt,
            payrollBody(
                command(
                    scene.publisher.client,
                    publicationPath(scene) + "/${scene.id}/recover",
                    body,
                    scene.publisher.csrf,
                    key,
                )
            ),
        )
        assertEquals("APPROVED", publicationView(scene)[0]["amendment"]["status"].asString())
        assertTrue(stepPublication(scene, lease) is Result.Failed)
    }

    @Test
    fun anAuthorizedReplacementCanRecoverAnExhaustedPublicationAfterTheInitiatorLosesAccess() {
        val scene = publicationScene()
        val lease = publicationLease(scene)
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        assertEquals(
            Result.Success(true),
            transactions.run(payrollActor(scene.source.payroll, scene.publisher)) {
                publicationJobs.failExpired(lease, "job_attempt_limit")
            },
        )
        val jobVersion = publicationView(scene)[0]["job"]["version"].asLong()
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.amend.publish'",
                scene.source.payroll.company,
                scene.publisher.account,
            )
        val replacement =
            payrollMember(
                scene.source.payroll.company,
                setOf("company.read", "payroll.amend.publish"),
            )
        val key = UUID.randomUUID()
        val path = publicationPath(scene) + "/${scene.id}/recover"
        val body =
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to 4,
                    "expectedJobVersion" to jobVersion,
                    "reason" to "Authorized operator recovery",
                )
            )
        payrollError(
            command(scene.publisher.client, path, body, scene.publisher.csrf, key),
            403,
            "access_denied",
        )
        val receipt = payrollBody(command(replacement.client, path, body, replacement.csrf, key))
        assertEquals(
            receipt,
            payrollBody(command(replacement.client, path, body, replacement.csrf, key)),
        )
        val retained = payrollBody(get(replacement.client, publicationPath(scene)))[0]
        assertEquals("STOPPED", retained["publication"]["status"].asString())
        assertEquals(
            replacement.account.toString(),
            retained["publication"]["completedBy"].asString(),
        )
        assertEquals("APPROVED", retained["amendment"]["status"].asString())
        assertEquals(0, currentPayslip(scene)["summary"]["version"].asLong())
    }
}
