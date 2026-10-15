package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentReviewAccessHttpTest : PayrollAmendmentReviewApiFixture() {
    @Test
    fun templatesRequireIndependentAssignmentsAndTheDedicatedReviewGrant() {
        val f = payrollFixture()
        val id = UUID.randomUUID()
        val stages =
            listOf(
                mapOf("assignment" to "MANAGER") to "payroll_approval_assignment_invalid",
                mapOf("assignment" to "PERMISSION", "permission" to "payroll.review") to
                    "invalid_approval_stage",
                mapOf("assignment" to "PERMISSION", "permission" to "payroll.amend.review") to null,
            )
        for ((stage, failure) in stages) {
            val response =
                command(
                    f.admin,
                    "/api/v1/companies/${f.company}/approvals/templates/$id",
                    json.writeValueAsString(
                        mapOf(
                            "name" to "Independent amendment review",
                            "kind" to "PAYROLL_AMENDMENT",
                            "effectiveFrom" to "2026-01-01",
                            "minimumAmount" to "0",
                            "stages" to listOf(stage),
                            "reason" to "Independent correction review",
                        )
                    ),
                    f.adminCsrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            if (failure == null) payrollBody(response) else payrollError(response, 422, failure)
        }
        assertEquals(1, count(f.company, "approval_templates"))
    }

    @Test
    fun historicalPreparersSubmitterAndBeneficiaryCannotApproveEvenWhenNamedOrGrantedReview() {
        val initial = amendmentReviewScene(submitted = false)
        val draft = initial.draft
        val f = draft.source
        val grants =
            setOf("company.read", "payroll.amend", "payroll.amend.review", "approvals.read")
        val editor = payrollMember(f.payroll.company, grants)
        val submitter = payrollMember(f.payroll.company, grants)
        for (account in listOf(f.maker.account, f.payroll.owner.account)) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.amend.review')",
                f.payroll.company,
                account,
            )
        payrollBody(
            saveDraft(f, draft.id, draftBody(f, amendmentPreparation(f), 1), member = editor)
        )
        payrollBody(draftCommand(draft, "calculate", 2, member = editor))
        val scene =
            initial.copy(
                calculationId =
                    UUID.fromString(draftView(draft)["latestCalculation"]["id"].asString())
            )
        amendmentReviewTemplate(
            draft,
            listOf(scene.reviewer.account),
            changes =
                mapOf(
                    "minimumAmount" to "1",
                    "stages" to
                        listOf(
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to
                                    listOf(
                                        f.maker.account,
                                        editor.account,
                                        submitter.account,
                                        f.payroll.owner.account,
                                        scene.reviewer.account,
                                    ),
                            )
                        ),
                ),
        )
        payrollBody(submitAmendmentReview(scene, 3, submitter))
        val view = amendmentReviewView(scene)
        assertEquals(
            listOf(scene.reviewer.account.toString()),
            view["approval"]["stages"][0].toList().map { it.asString() },
        )
        val excluded = view["approval"]["excludedAccountIds"].toList().map { it.asString() }.toSet()
        assertTrue(
            excluded.containsAll(
                listOf(f.maker.account, editor.account, f.payroll.owner.account).map(UUID::toString)
            )
        )
        assertEquals(submitter.account.toString(), view["approval"]["authorId"].asString())
        val before = amendmentReviewCounts(scene)
        for (member in listOf(f.maker, editor, submitter, f.payroll.owner)) payrollError(
            decideAmendmentReview(scene, version = 4, member = member),
            403,
            "self_approval_denied",
        )
        assertEquals(before, amendmentReviewCounts(scene))
        payrollBody(decideAmendmentReview(scene, version = 4))
    }

    @Test
    fun reviewReadAuthorityDoesNotGrantPreparationAndOrdinaryPayrollReviewDoesNotGrantAmendmentAccess() {
        val scene = amendmentReviewScene()
        val draft = scene.draft
        val f = draft.source
        val ordinary = payrollMember(f.payroll.company, setOf("company.read", "payroll.review"))
        for (path in
            listOf(
                amendmentReviewPath(scene),
                amendmentReviewsPath(scene),
                draftsPath(f) + "/${draft.id}",
                draftsPath(f) + "/${draft.id}/history",
                draftsPath(f) + "/${draft.id}/revisions/1",
                draftsPath(f) + "/${draft.id}/calculations/${scene.calculationId}",
                draftsPath(f) + "?year=2026",
            )) {
            payrollError(get(ordinary.client, path), 403, "access_denied")
            payrollError(get(f.payroll.admin, path), 403, "access_denied")
            payrollBody(get(scene.reviewer.client, path))
        }
        payrollError(decideAmendmentReview(scene, member = ordinary), 403, "access_denied")
        payrollError(
            submitAmendmentReview(scene.copy(id = UUID.randomUUID()), member = scene.reviewer),
            403,
            "access_denied",
        )
        payrollError(withdrawAmendmentReview(scene, member = scene.reviewer), 403, "access_denied")
        assertEquals(
            0,
            payrollBody(get(scene.reviewer.client, draftsPath(f) + "/${draft.id}"))[
                    "availableActions"]
                .size(),
        )
    }

    @Test
    fun delegationAuthorityIsCheckedAgainBeforeDecisionAndReceiptReplay() {
        val scene = amendmentReviewScene()
        val f = scene.draft.source.payroll
        val expires = clock.instant().plusSeconds(60)
        val delegate = delegatedReviewer(scene, expires)
        val key = UUID.randomUUID()
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.amend.review'",
                f.company,
                scene.reviewer.account,
            )
        payrollError(
            decideAmendmentReview(scene, member = delegate, key = key),
            403,
            "not_assigned_approver",
        )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.amend.review')",
                f.company,
                scene.reviewer.account,
            )
        val before = amendmentReviewCounts(scene)
        val now = clock.instant()
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.amendment_review_decided") clock.set(expires)
        }
        try {
            payrollError(
                decideAmendmentReview(scene, member = delegate, key = key),
                403,
                "not_assigned_approver",
            )
            assertEquals(before, amendmentReviewCounts(scene))
        } finally {
            payrollProbe.clear()
            clock.set(now)
        }
        val receipt = payrollBody(decideAmendmentReview(scene, member = delegate, key = key))
        assertEquals(
            receipt,
            payrollBody(decideAmendmentReview(scene, member = delegate, key = key)),
        )
        try {
            clock.set(expires)
            payrollError(
                decideAmendmentReview(scene, member = delegate, key = key),
                403,
                "not_assigned_approver",
            )
        } finally {
            clock.set(now)
        }
        val decision = amendmentReviewView(scene)["history"][1]
        assertEquals(delegate.account.toString(), decision["actorId"].asString())
        assertEquals(scene.reviewer.account.toString(), decision["decidingFor"].asString())
    }

    @Test
    fun initialAccountBindingExcludesTheNewBeneficiaryAndTheirDelegateWithoutRewritingApproval() {
        for (bindAfterApproval in listOf(false, true)) {
            val payroll = payrollFixture(bindAccount = false)
            val processing = processingFixture(payroll)
            val calculation = calculationFixture(f = processing)
            val scene = amendmentReviewScene(draftFixture(amendmentFixture(calculation)))
            val delegate = delegatedReviewer(scene, clock.instant().plusSeconds(600))
            val key = UUID.randomUUID()
            val original = amendmentReviewView(scene)["approval"]
            if (bindAfterApproval)
                payrollBody(decideAmendmentReview(scene, member = delegate, key = key))
            val beneficiary = if (bindAfterApproval) delegate else scene.reviewer
            payrollBody(
                command(
                    payroll.admin,
                    "/api/v1/companies/${payroll.company}/employees/${payroll.employee}/account-link",
                    json.writeValueAsString(
                        mapOf(
                            "accountId" to beneficiary.account,
                            "expectedVersion" to 0,
                            "reason" to "Identity verified independently",
                        )
                    ),
                    payroll.adminCsrf,
                    UUID.randomUUID(),
                )
            )
            val current =
                beneficiary.copy(
                    csrf = login(beneficiary.client, "${beneficiary.account}@example.test")
                )
            payrollError(
                decideAmendmentReview(scene, member = current, key = key),
                403,
                "self_approval_denied",
            )
            if (!bindAfterApproval) {
                payrollError(
                    decideAmendmentReview(scene, member = delegate, key = key),
                    403,
                    "not_assigned_approver",
                )
            }
            val retained = amendmentReviewView(scene, member = scene.draft.source.maker)["approval"]
            assertEquals(original["authorId"], retained["authorId"])
            assertNull(
                database()
                    .queryForObject(
                        "select requester_id from approval_requests where company_id=? and id=?",
                        UUID::class.java,
                        payroll.company,
                        UUID.fromString(original["id"].asString()),
                    )
            )
            assertEquals(original["excludedAccountIds"], retained["excludedAccountIds"])
            assertEquals(original["stages"], retained["stages"])
        }
    }

    @Test
    fun companyScopeProtectsReviewDiscoveryCommandsAndRawRows() {
        val scene = amendmentReviewScene()
        val other = payrollFixture()
        val outsider =
            payrollMember(
                other.company,
                setOf("company.read", "payroll.amend", "payroll.amend.review"),
            )
        val foreign =
            scene.copy(
                draft =
                    scene.draft.copy(
                        source =
                            scene.draft.source.copy(
                                publication =
                                    scene.draft.source.publication.copy(
                                        calculation =
                                            scene.draft.source.publication.calculation.copy(
                                                people =
                                                    scene.draft.source.publication.calculation
                                                        .people
                                                        .copy(payroll = other)
                                            )
                                    ),
                                maker = outsider,
                            )
                    )
            )
        payrollError(
            get(outsider.client, amendmentReviewPath(foreign)),
            404,
            "payroll_amendment_review_not_found",
        )
        payrollError(
            get(outsider.client, amendmentReviewsPath(foreign)),
            404,
            "payroll_amendment_not_found",
        )
        payrollError(
            decideAmendmentReview(foreign, member = outsider),
            404,
            "payroll_amendment_review_not_found",
        )
        val scoped =
            transactions.run(payrollActor(other, outsider)) {
                for (table in
                    listOf(
                        "payroll_amendment_reviews",
                        "payroll_amendment_review_changes",
                    )) assertEquals(
                    0,
                    runtimeJdbc.queryForObject("select count(*) from $table", Int::class.java),
                )
                amendmentReviews.find(other.company, scene.id)
            }
        assertEquals(Result.Success(null), scoped)
    }

    private fun delegatedReviewer(
        scene: AmendmentReviewScene,
        expires: java.time.Instant,
    ): PayrollMember {
        val company = scene.draft.source.payroll.company
        val delegate =
            payrollMember(company, setOf("company.read", "payroll.amend.review", "approvals.read"))
        payrollBody(
            command(
                scene.reviewer.client,
                "/api/v1/companies/$company/approvals/delegations/${UUID.randomUUID()}",
                json.writeValueAsString(
                    mapOf(
                        "kind" to "PAYROLL_AMENDMENT",
                        "fromAccount" to scene.reviewer.account,
                        "toAccount" to delegate.account,
                        "validFrom" to clock.instant().minusSeconds(1).toString(),
                        "validUntil" to expires.toString(),
                        "reason" to "Independent review coverage",
                    )
                ),
                scene.reviewer.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        return delegate
    }
}
