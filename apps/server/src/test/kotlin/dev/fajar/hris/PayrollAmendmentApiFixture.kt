package dev.fajar.hris

import dev.fajar.hris.payroll.domain.usecases.GetPayrollAmendmentPreparation
import dev.fajar.hris.payroll.domain.usecases.PreviewPayrollAmendment
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(PayrollAmendmentProbeConfiguration::class)
abstract class PayrollAmendmentApiFixture : PayrollTaxContinuityApiFixture() {
    @Autowired protected lateinit var amendmentProbe: PayrollAmendmentProbe
    @Autowired protected lateinit var getAmendmentPreparation: GetPayrollAmendmentPreparation
    @Autowired protected lateinit var previewAmendment: PreviewPayrollAmendment

    protected data class AmendmentFixture(
        val publication: PublicationFixture,
        val assessment: UUID,
        val maker: PayrollMember,
    ) {
        val payroll
            get() = publication.calculation.people.payroll
    }

    @AfterEach fun clearAmendmentProbe() = amendmentProbe.clear()

    protected fun amendmentFixture(): AmendmentFixture {
        val f = calculationFixture()
        reviewTemplate(f)
        val (publication, assessment) = publishTaxRun(f, beginTaxRun(f))
        return AmendmentFixture(
            publication,
            assessment,
            payrollMember(f.people.payroll.company, setOf("company.read", "payroll.amend")),
        )
    }

    protected fun amendmentPath(f: AmendmentFixture, id: UUID = f.assessment) =
        "/api/v1/companies/${f.payroll.company}/payroll/payslips/$id"

    protected fun amendmentPreparation(f: AmendmentFixture) =
        payrollBody(get(f.maker.client, amendmentPath(f) + "/amendment-preparation"))

    protected fun remuneration(
        preparation: JsonNode,
        index: Int = 0,
        difference: String = "1000000",
    ): Map<String, Any?> {
        val entry = preparation["assessments"][index]
        val compensation: MutableMap<String, Any?> =
            entry["compensation"]
                .propertyNames()
                .associateWith { entry["compensation"][it] }
                .toMutableMap()
        compensation["basicSalary"] =
            (BigDecimal(entry["compensation"]["basicSalary"].asString()) + BigDecimal(difference))
                .toPlainString()
        return mapOf(
            "assessmentId" to entry["source"]["assessmentId"].asString(),
            "compensation" to compensation,
        )
    }

    protected fun amendmentPreviewBody(
        preparation: JsonNode,
        corrections: List<Map<String, Any?>> = listOf(remuneration(preparation)),
    ) =
        json.writeValueAsString(
            mapOf("sourceVersions" to preparation["sourceVersions"], "corrections" to corrections)
        )

    protected fun amendmentPreview(
        f: AmendmentFixture,
        body: String,
        member: PayrollMember = f.maker,
    ) =
        command(
            member.client,
            amendmentPath(f) + "/amendment-preview",
            body,
            member.csrf,
            UUID.randomUUID(),
        )
}
