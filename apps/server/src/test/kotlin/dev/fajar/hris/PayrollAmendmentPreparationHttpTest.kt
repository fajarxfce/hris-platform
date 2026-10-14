package dev.fajar.hris

import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentPreparationHttpTest : PayrollAmendmentApiFixture() {
    @Test
    fun preparationAndRecalculationRetainOriginalPayrollAndCreateNoPublicationOrPayment() {
        val f = amendmentFixture()
        val before = payrollBody(get(f.payroll.owner.client, amendmentPath(f)))
        val counts =
            listOf(
                    "payroll_run_results",
                    "payroll_assessments",
                    "payroll_finalizations",
                    "payroll_payment_batches",
                    "operation_receipts",
                    "background_jobs",
                )
                .associateWith { count(f.payroll.company, it) }
        val response = get(f.maker.client, amendmentPath(f) + "/amendment-preparation")
        assertTrue(response.headers().allValues("Cache-Control").any { it.contains("no-store") })
        val preparation = payrollBody(response)
        assertEquals(f.assessment.toString(), preparation["firstAssessmentId"].asString())
        assertEquals(1, preparation["assessments"].size())
        assertEquals(0, preparation["sourceVersions"][0]["revision"].asLong())
        assertFalse(preparation.toString().contains("personId"))
        assertFalse(preparation.toString().contains("preparedBy"))
        val body = amendmentPreviewBody(preparation)
        val preview = payrollBody(amendmentPreview(f, body))
        val entry = preview["calculations"].single()
        assertEquals(before["calculation"], entry["original"])
        assertEquals(preparation["sourceVersions"], preview["sourceVersions"])
        assertEquals("2026-09", entry["month"].asString())
        assertEquals(
            0,
            BigDecimal("1000000")
                .compareTo(BigDecimal(preview["difference"]["taxableGross"].asString())),
        )
        assertTrue(BigDecimal(preview["difference"]["takeHome"].asString()).signum() > 0)
        assertEquals(preview, payrollBody(amendmentPreview(f, body)))
        assertEquals(before, payrollBody(get(f.payroll.owner.client, amendmentPath(f))))
        assertEquals(counts, counts.keys.associateWith { count(f.payroll.company, it) })
    }

    @Test
    fun newlyPublishedMonthsInvalidateAnOlderReviewAndAreIncludedInTheNextRecalculation() {
        val f = amendmentFixture()
        val initial = amendmentPreparation(f)
        val originalBody = amendmentPreviewBody(initial)
        val second = followingMonth(f.publication.calculation, YearMonth.of(2026, 10))
        val (_, october) = publishTaxRun(second, beginTaxRun(second))
        val current =
            f.copy(
                maker =
                    f.maker.copy(csrf = login(f.maker.client, "${f.maker.account}@example.test"))
            )
        payrollError(
            amendmentPreview(current, originalBody),
            409,
            "payroll_amendment_sources_changed",
        )
        val updated = amendmentPreparation(current)
        assertEquals(
            listOf("2026-09", "2026-10"),
            updated["assessments"].toList().map { it["month"].asString() },
        )
        assertEquals(october.toString(), updated["sourceVersions"][1]["assessmentId"].asString())
        val preview = payrollBody(amendmentPreview(current, amendmentPreviewBody(updated)))
        assertEquals(2, preview["calculations"].size())
        assertEquals(
            0,
            BigDecimal(preview["calculations"][1]["difference"]["taxableGross"].asString()).signum(),
        )
        val priorGross =
            BigDecimal(preview["calculations"][0]["calculation"]["tax"]["taxableGross"].asString())
        val initialHistory =
            BigDecimal(
                preview["calculations"][0]["calculation"]["taxInput"]["history"]["taxableGross"]
                    .asString()
            )
        val octoberHistory =
            BigDecimal(
                preview["calculations"][1]["calculation"]["taxInput"]["history"]["taxableGross"]
                    .asString()
            )
        assertEquals(0, (initialHistory + priorGross).compareTo(octoberHistory))
        val octoberPreparation =
            payrollBody(
                get(
                    current.maker.client,
                    amendmentPath(current, october) + "/amendment-preparation",
                )
            )
        val octoberBody =
            amendmentPreviewBody(octoberPreparation, listOf(remuneration(octoberPreparation, 1)))
        val octoberOnly =
            payrollBody(
                command(
                    current.maker.client,
                    amendmentPath(current, october) + "/amendment-preview",
                    octoberBody,
                    current.maker.csrf,
                    UUID.randomUUID(),
                )
            )
        assertEquals(2, octoberOnly["sourceVersions"].size())
        assertEquals(1, octoberOnly["calculations"].size())
    }

    @Test
    fun amendmentAuthorityIsIndependentAndNeverExposesAnotherCompanyOrTheBeneficiarysPreparation() {
        val f = amendmentFixture()
        val body = amendmentPreviewBody(amendmentPreparation(f))
        assertTrue("payroll.amend" in PermissionCatalog.assignable)
        for (defaults in
            listOf(
                PermissionCatalog.companyAdministrator,
                PermissionCatalog.humanResources,
                PermissionCatalog.finance,
                PermissionCatalog.employee,
            )) assertFalse("payroll.amend" in defaults)
        for (member in listOf(f.payroll.operator, f.payroll.owner, f.publication.finalizer)) {
            payrollError(
                get(member.client, amendmentPath(f) + "/amendment-preparation"),
                403,
                "access_denied",
            )
            payrollError(amendmentPreview(f, body, member), 403, "access_denied")
        }
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.amend')",
                f.payroll.company,
                f.payroll.owner.account,
            )
        payrollError(
            get(f.payroll.owner.client, amendmentPath(f) + "/amendment-preparation"),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
        payrollError(
            amendmentPreview(f, body, f.payroll.owner),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
        val foreign = payrollFixture()
        val maker = payrollMember(foreign.company, setOf("company.read", "payroll.amend"))
        payrollError(
            get(maker.client, amendmentPath(f) + "/amendment-preparation"),
            403,
            "company_access_denied",
        )
        payrollError(
            get(
                maker.client,
                "/api/v1/companies/${foreign.company}/payroll/payslips/${f.assessment}/amendment-preparation",
            ),
            404,
            "payroll_assessment_not_found",
        )
        payrollError(
            get(f.maker.client, amendmentPath(f, UUID.randomUUID()) + "/amendment-preparation"),
            404,
            "payroll_assessment_not_found",
        )
    }

    @Test
    fun versionPreconditionsAndCorrectionMembershipCannotBeSilentlyReplaced() {
        val f = amendmentFixture()
        val preparation = amendmentPreparation(f)
        val valid = remuneration(preparation)
        for (versions in
            listOf(
                listOf(mapOf("assessmentId" to f.assessment, "revision" to 1)),
                listOf(mapOf("assessmentId" to UUID.randomUUID(), "revision" to 0)),
            )) {
            val body =
                json.writeValueAsString(
                    mapOf("sourceVersions" to versions, "corrections" to listOf(valid))
                )
            payrollError(amendmentPreview(f, body), 409, "payroll_amendment_sources_changed")
        }
        for (changes in
            listOf(
                emptyList(),
                listOf(valid, valid),
                listOf(mapOf("assessmentId" to f.assessment)),
                listOf(valid + ("assessmentId" to UUID.randomUUID())),
                List(13) { valid },
            )) {
            payrollError(
                amendmentPreview(f, amendmentPreviewBody(preparation, changes)),
                422,
                "invalid_payroll_amendment_corrections",
            )
        }
        for (version in listOf(-1L, Long.MAX_VALUE)) {
            val body =
                json.writeValueAsString(
                    mapOf(
                        "sourceVersions" to
                            listOf(mapOf("assessmentId" to f.assessment, "revision" to version)),
                        "corrections" to listOf(valid),
                    )
                )
            payrollError(amendmentPreview(f, body), 422, "invalid_payroll_amendment_corrections")
        }
    }

    @Test
    fun verifiedTaxEvidenceCannotBeReplacedByAClientRemunerationProposal() {
        val f = amendmentFixture()
        val preparation = amendmentPreparation(f)
        val original = preparation["assessments"][0]["compensation"]
        val tax: Map<String, Any?> =
            original["tax"].propertyNames().associateWith { original["tax"][it] } +
                ("verificationReference" to "Unreviewed replacement")
        val compensation: Map<String, Any?> =
            original.propertyNames().associateWith { original[it] } + ("tax" to tax)
        val body =
            amendmentPreviewBody(
                preparation,
                listOf(mapOf("assessmentId" to f.assessment, "compensation" to compensation)),
            )
        payrollError(amendmentPreview(f, body), 409, "payroll_amendment_tax_evidence_changed")
        val missingBasis =
            original.propertyNames().associateWith { original[it] }.toMutableMap<String, Any?>()
        missingBasis["payBasis"] = null
        val invalid =
            amendmentPreviewBody(
                preparation,
                listOf(mapOf("assessmentId" to f.assessment, "compensation" to missingBasis)),
            )
        val response = amendmentPreview(f, invalid)
        payrollError(response, 409, "payroll_pay_basis_required")
        val error = json.readTree(response.body())
        assertEquals(f.assessment.toString(), error["parameters"]["assessmentId"].asString())
        assertEquals("2026-09", error["parameters"]["taxMonth"].asString())
    }
}
