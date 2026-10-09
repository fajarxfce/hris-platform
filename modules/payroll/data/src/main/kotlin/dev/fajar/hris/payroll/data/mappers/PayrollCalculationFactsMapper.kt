package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.math.BigDecimal
import java.time.*

fun PayrollCalculationFacts.toSnapshotData() =
    PayrollCalculationFactsData(
        month = month.toString(),
        incomeDueDate = incomeDueDate,
        plannedPaymentDate = plannedPaymentDate,
        policy = policy.toSnapshotData(),
        compensation = compensation.toData(),
        input = input.toData(),
        employment = java.util.List.copyOf(employment.map { item -> item.toSnapshotData() }),
        workDays = java.util.List.copyOf(workDays.map { item -> item.toSnapshotData() }),
        leaveDays = java.util.List.copyOf(leaveDays.map { item -> item.toSnapshotData() }),
        taxHistory = taxHistory.toData(),
    )

fun PayrollCalculationFactsData.toDomain() =
    PayrollCalculationFacts(
        month = YearMonth.parse(month),
        incomeDueDate = incomeDueDate,
        plannedPaymentDate = plannedPaymentDate,
        policy = policy.toDomain(),
        compensation = compensation.toTerms(),
        input = input.toTerms(),
        employment = java.util.List.copyOf(employment.map { item -> item.toDomain() }),
        workDays = java.util.List.copyOf(workDays.map { item -> item.toDomain() }),
        leaveDays = java.util.List.copyOf(leaveDays.map { item -> item.toDomain() }),
        taxHistory = taxHistory.toTerms(),
    )

fun PayrollPolicy.toSnapshotData() =
    PayrollPolicySnapshotData(
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

fun PayrollPolicySnapshotData.toDomain() =
    PayrollPolicy(
        version = version,
        appliedRevision = appliedRevision,
        effectiveFrom = YearMonth.parse(effectiveFrom),
        effectiveUntil = YearMonth.parse(effectiveUntil),
        incomeTaxRuleId = incomeTaxRuleId,
        insuranceRuleId = insuranceRuleId,
        minimumMonthlyWage = BigDecimal(minimumMonthlyWage),
        healthWageCap = BigDecimal(healthWageCap),
        pensionWageCap = BigDecimal(pensionWageCap),
        contributionRounding = ContributionRounding.valueOf(contributionRounding),
        reviewReferences = java.util.List.copyOf(reviewReferences),
    )

fun EmploymentTerms.toSnapshotData() =
    PayrollEmploymentTermsData(
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

fun PayrollEmploymentTermsData.toDomain() =
    EmploymentTerms(
        effectiveFrom = effectiveFrom,
        contract = ContractKind.valueOf(contract),
        startDate = startDate,
        endDate = endDate,
        status = EmploymentStatus.valueOf(status),
        branchId = branchId,
        departmentId = departmentId,
        positionId = positionId,
        costCenterId = costCenterId,
        managerId = managerId,
    )

fun PayrollWorkDay.toSnapshotData() =
    PayrollWorkDayData(
        date = date,
        schedule = schedule.name,
        attendance = attendance.name,
        officialHoliday = officialHoliday,
        overtime = java.util.List.copyOf(overtime.map { item -> item.toSnapshotData() }),
    )

fun PayrollWorkDayData.toDomain() =
    PayrollWorkDay(
        date = date,
        schedule = PayrollScheduleKind.valueOf(schedule),
        attendance = PayrollAttendanceKind.valueOf(attendance),
        officialHoliday = officialHoliday,
        overtime = java.util.List.copyOf(overtime.map { item -> item.toDomain() }),
    )

fun PayrollOvertimeEvidence.toSnapshotData() =
    PayrollOvertimeEvidenceData(requestId = requestId, revision = revision, minutes = minutes)

fun PayrollOvertimeEvidenceData.toDomain() =
    PayrollOvertimeEvidence(requestId = requestId, revision = revision, minutes = minutes)

fun PayrollLeaveDay.toSnapshotData() =
    PayrollLeaveDayData(
        requestId = requestId,
        revision = revision,
        date = date,
        portion = portion.name,
        paid = paid,
    )

fun PayrollLeaveDayData.toDomain() =
    PayrollLeaveDay(
        requestId = requestId,
        revision = revision,
        date = date,
        portion = PayrollDayPortion.valueOf(portion),
        paid = paid,
    )
