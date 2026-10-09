package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.time.*

fun PayrollCalculationFacts.toCalculationResponse() =
    PayrollCalculationFactsResponse(
        month = month.toString(),
        incomeDueDate = incomeDueDate,
        plannedPaymentDate = plannedPaymentDate,
        policy = policy.toCalculationResponse(),
        compensation = compensation.toResponse(),
        input = input.toResponse(),
        employment = java.util.List.copyOf(employment.map { item -> item.toCalculationResponse() }),
        workDays = java.util.List.copyOf(workDays.map { item -> item.toCalculationResponse() }),
        leaveDays = java.util.List.copyOf(leaveDays.map { item -> item.toCalculationResponse() }),
        taxHistory = taxHistory.toResponse(),
    )

fun PayrollPolicy.toCalculationResponse() =
    PayrollPolicySnapshotResponse(
        version = version,
        appliedRevision = appliedRevision,
        effectiveFrom = effectiveFrom.toString(),
        effectiveUntil = effectiveUntil.toString(),
        incomeTaxRuleId = incomeTaxRuleId,
        insuranceRuleId = insuranceRuleId,
        minimumMonthlyWage = minimumMonthlyWage.toPlainString(),
        healthWageCap = healthWageCap.toPlainString(),
        pensionWageCap = pensionWageCap.toPlainString(),
        contributionRounding = contributionRounding.name,
        reviewReferences = java.util.List.copyOf(reviewReferences),
    )

fun EmploymentTerms.toCalculationResponse() =
    PayrollEmploymentTermsResponse(
        effectiveFrom = effectiveFrom,
        contract = contract.name,
        startDate = startDate,
        endDate = endDate,
        status = status.name,
        branchId = branchId,
        departmentId = departmentId,
        positionId = positionId,
        costCenterId = costCenterId,
        managerId = managerId,
    )

fun PayrollWorkDay.toCalculationResponse() =
    PayrollWorkDayResponse(
        date = date,
        schedule = schedule.name,
        attendance = attendance.name,
        officialHoliday = officialHoliday,
        overtime = java.util.List.copyOf(overtime.map { item -> item.toCalculationResponse() }),
    )

fun PayrollOvertimeEvidence.toCalculationResponse() =
    PayrollOvertimeEvidenceResponse(requestId = requestId, revision = revision, minutes = minutes)

fun PayrollLeaveDay.toCalculationResponse() =
    PayrollLeaveDayResponse(
        requestId = requestId,
        revision = revision,
        date = date,
        portion = portion.name,
        paid = paid,
    )
