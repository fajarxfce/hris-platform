package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.*

fun EmployeeImport.toResponse() =
    EmployeeImportResponse(
        id,
        fileName,
        sourceHash,
        rowCount,
        status.name,
        jobId,
        version,
        createdBy,
        createdAt,
        reason,
    )

fun EmployeeImportSummary.toResponse() =
    EmployeeImportSummaryResponse(batch.toResponse(), counts.mapKeys { it.key.name })

fun EmployeeDraft.toImportResponse() =
    EmployeeImportProposalResponse(
        id,
        employeeNumber,
        person.legalName,
        person.birthDate,
        person.nationality,
        person.email,
        terms.toResponse(),
    )

fun EmployeeImportRow.toResponse() =
    EmployeeImportRowResponse(
        number,
        employeeNumber,
        legalName,
        status.name,
        parseIssues + issues,
        draft?.toImportResponse(),
        createdEmploymentId,
    )

fun EmployeeImportAttempt.toResponse() =
    EmployeeImportAttemptResponse(jobId, phase.name, actorId, createdAt)
