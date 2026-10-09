package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollPolicy.toResponse() =
    PayrollPolicyResponse(
        version,
        appliedRevision,
        effectiveFrom,
        effectiveUntil,
        "IDR",
        incomeTaxRuleId,
        insuranceRuleId,
        minimumMonthlyWage.toPlainString(),
        healthWageCap.toPlainString(),
        pensionWageCap.toPlainString(),
        contributionRounding.name,
        reviewReferences,
    )

fun PayrollPolicyRevision.toResponse() =
    PayrollPolicyRevisionResponse(policy.toResponse(), actorId, reason, recordedAt)

fun IncomeTaxBand.toResponse() =
    IncomeTaxBandResponse(upperInclusive?.toPlainString(), rate.toPlainString())

fun IncomeTaxRules.toResponse() =
    IncomeTaxRuleResponse(
        id,
        effectiveFrom,
        effectiveUntil,
        sources,
        monthly.mapKeys { it.key.name }.mapValues { it.value.map { band -> band.toResponse() } },
        progressive.map { it.toResponse() },
        allowances.mapKeys { it.key.name }.mapValues { it.value.toPlainString() },
        categories.mapKeys { it.key.name }.mapValues { it.value.name },
        jobExpenseRate.toPlainString(),
        monthlyJobExpenseCap.toPlainString(),
        nonResidentRate.toPlainString(),
    )
