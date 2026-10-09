package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.YearMonth
import tools.jackson.databind.ObjectMapper

fun PayrollPolicy.toData() =
    PayrollPolicyData(
        incomeTaxRuleId,
        insuranceRuleId,
        minimumMonthlyWage.toPlainString(),
        healthWageCap.toPlainString(),
        pensionWageCap.toPlainString(),
        contributionRounding.name,
        reviewReferences,
    )

fun PayrollPolicyRow.toPolicy(json: ObjectMapper): PayrollPolicy {
    val data = json.readValue(revision.details.data(), PayrollPolicyData::class.java)
    return PayrollPolicy(
        version,
        revision.revision,
        YearMonth.from(revision.effectiveFrom),
        YearMonth.from(revision.effectiveUntil),
        data.incomeTaxRuleId,
        data.insuranceRuleId,
        BigDecimal(data.minimumMonthlyWage),
        BigDecimal(data.healthWageCap),
        BigDecimal(data.pensionWageCap),
        ContributionRounding.valueOf(data.contributionRounding),
        java.util.List.copyOf(data.reviewReferences),
    )
}

fun PayrollPolicyRow.toHistory(json: ObjectMapper) =
    PayrollPolicyRevision(
        toPolicy(json),
        revision.actorId,
        revision.reason,
        revision.recordedAt.toInstant(),
    )
