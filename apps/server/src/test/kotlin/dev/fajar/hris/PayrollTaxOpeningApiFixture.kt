package dev.fajar.hris

import dev.fajar.hris.payroll.domain.usecases.SavePayrollTaxOpening
import dev.fajar.hris.payroll.domain.usecases.VerifyPayrollTaxOpening
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PayrollTaxOpeningProbeConfiguration::class)
abstract class PayrollTaxOpeningApiFixture : PayrollApiFixture() {
    @Autowired protected lateinit var openingProbe: PayrollTaxOpeningProbe
    @Autowired protected lateinit var saveOpening: SavePayrollTaxOpening
    @Autowired protected lateinit var verifyOpening: VerifyPayrollTaxOpening

    protected data class OpeningFixture(
        val payroll: PayrollFixture,
        val preparer: PayrollMember,
        val reviewer: PayrollMember,
    )

    @AfterEach
    fun clearOpeningHooks() {
        openingProbe.clear()
    }

    protected fun openingFixture(): OpeningFixture {
        val f = payrollFixture()
        return OpeningFixture(
            f,
            payrollMember(f.company, setOf("company.read", "payroll.calculate", "payroll.review")),
            payrollMember(f.company, setOf("company.read", "payroll.review")),
        )
    }

    protected fun openingTerms(
        changes: Map<String, Any?> = emptyMap(),
        historyChanges: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        mapOf(
            "throughMonth" to 6,
            "residency" to "RESIDENT",
            "ptkp" to "K0",
            "reference" to "Reviewed fictional June payroll records",
            "history" to
                (mapOf(
                    "taxableGross" to "60000000",
                    "retirementContributions" to "600000",
                    "qualifiedDonations" to "0",
                    "withheld" to "1500000",
                    "employmentMonths" to 6,
                    "previousEmployerNet" to "0",
                    "previousEmployerWithheld" to "0",
                ) + historyChanges),
        ) + changes

    protected fun openingBody(
        version: Long? = null,
        changes: Map<String, Any?> = emptyMap(),
        termChanges: Map<String, Any?> = emptyMap(),
        historyChanges: Map<String, Any?> = emptyMap(),
    ): String =
        json.writeValueAsString(
            mapOf(
                "terms" to openingTerms(termChanges, historyChanges),
                "expectedVersion" to version,
                "expectedEmploymentVersion" to 0,
                "reason" to "Prepare verified opening history",
            ) + changes
        )

    protected fun openingPath(
        f: OpeningFixture,
        employee: UUID = f.payroll.employee,
        year: Int = 2026,
    ) = "/api/v1/companies/${f.payroll.company}/payroll/employees/$employee/tax-openings/$year"

    protected fun opening(
        f: OpeningFixture,
        body: String = openingBody(),
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.preparer,
        employee: UUID = f.payroll.employee,
        year: Int = 2026,
    ) = command(member.client, openingPath(f, employee, year), body, member.csrf, key, "PUT")

    protected fun verify(
        f: OpeningFixture,
        version: Long = 0,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.reviewer,
        employee: UUID = f.payroll.employee,
    ) =
        command(
            member.client,
            openingPath(f, employee) + "/verify",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Independent opening review")
            ),
            member.csrf,
            key,
        )
}
