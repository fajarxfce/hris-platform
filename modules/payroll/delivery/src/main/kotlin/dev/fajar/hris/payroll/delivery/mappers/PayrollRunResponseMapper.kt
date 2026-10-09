package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.*

fun PayrollRun.toResponse() =
    PayrollRunResponse(
        id,
        periodId,
        number,
        earningsMonth.toString(),
        earningsMonth.toString(),
        incomeDueDate,
        plannedPaymentDate,
        timezone,
        workJobId,
        workPeriodVersion,
        policyRevision,
        totalEmployees,
        actorId,
        reviewReference,
        reason,
        createdAt,
        jobId,
        status.name,
        version,
        processed,
        succeeded,
        failed,
    )

fun PayrollRunTarget.toResponse() =
    PayrollRunTargetResponse(
        ordinal,
        employeeId,
        employeeNumber,
        employeeName,
        employmentVersion,
        compensationRevision,
        inputId,
        inputRevision,
        taxOpeningId,
        taxOpeningRevision,
    )

fun PayrollRunItem.toResponse() =
    PayrollRunItemResponse(
        target.toResponse(),
        jobId,
        completedAt,
        failure?.let { PayrollRunFailureResponse(it.kind.name, it.code, it.fields, it.parameters) },
        taxableGross?.toPlainString(),
        withheld?.toPlainString(),
        takeHome?.toPlainString(),
    )

fun PayrollRunResult.toResponse() =
    PayrollRunResultResponse(
        item.toResponse(),
        facts?.toCalculationResponse(),
        calculation?.toCalculationResponse(),
    )

fun PayrollRunDetails.toResponse() =
    PayrollRunDetailsResponse(
        run.toResponse(),
        attempts.map {
            PayrollRunAttemptResponse(
                it.jobId,
                it.number,
                it.baseCompleted,
                it.startedAt,
                it.reason,
            )
        },
        PayrollRunProgressResponse(
            job.request.id,
            job.status.name,
            job.completedItems,
            job.request.totalItems,
            job.attempts,
            job.version,
            job.cancellationRequested,
            job.failureCode,
        ),
        Page(results.items.map { it.toResponse() }, results.nextCursor),
    )
