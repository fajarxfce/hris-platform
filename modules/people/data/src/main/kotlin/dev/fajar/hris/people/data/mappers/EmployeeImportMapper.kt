package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.people.data.models.EmployeeDraftData
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun EmployeeImportsRecord.toEmployeeImport() =
    EmployeeImport(
        id,
        fileName,
        sourceHash,
        rowCount,
        EmployeeImportStatus.valueOf(status),
        jobId,
        version,
        createdBy,
        createdAt.toInstant(),
        reason,
    )

fun EmployeeImport.toRow(company: UUID) =
    EmployeeImportsRecord().also {
        it.companyId = company
        it.id = id
        it.fileName = fileName
        it.sourceHash = sourceHash
        it.rowCount = rowCount
        it.status = status.name
        it.jobId = jobId
        it.version = version
        it.createdBy = createdBy
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }

fun EmployeeDraft.toData() =
    EmployeeDraftData(
        id,
        employeeNumber,
        person.id,
        person.legalName,
        person.nationality,
        person.email,
        person.birthDate,
        terms.startDate,
        terms.endDate,
        terms.contract.name,
        terms.branchId,
        terms.departmentId,
        terms.positionId,
        terms.costCenterId,
        terms.managerId,
    )

fun EmployeeDraftData.toDraft() =
    EmployeeDraft(
        id,
        employeeNumber,
        PersonProfile(personId, null, legalName, birthDate, nationality, email),
        EmploymentTerms(
            startDate,
            ContractKind.valueOf(contract),
            startDate,
            endDate,
            EmploymentStatus.ACTIVE,
            branchId,
            departmentId,
            positionId,
            costCenterId,
            managerId,
        ),
    )

fun importIssues(value: JSONB, json: ObjectMapper): Map<String, String> =
    json.readValue(
        value.data(),
        json.typeFactory.constructMapType(Map::class.java, String::class.java, String::class.java),
    )

fun EmployeeImportRowsRecord.toEmployeeImportRow(json: ObjectMapper) =
    EmployeeImportRow(
        rowNumber,
        employeeNumber,
        legalName,
        proposed?.let { json.readValue(it.data(), EmployeeDraftData::class.java).toDraft() },
        importIssues(parseIssues, json),
        EmployeeImportRowStatus.valueOf(status),
        importIssues(issues, json),
        createdEmploymentId,
    )

fun EmployeeImportRow.toRow(company: UUID, id: UUID, json: ObjectMapper) =
    EmployeeImportRowsRecord().also {
        it.companyId = company
        it.importId = id
        it.rowNumber = number
        it.employeeNumber = employeeNumber
        it.legalName = legalName
        it.proposed = draft?.let { value -> JSONB.valueOf(json.writeValueAsString(value.toData())) }
        it.parseIssues = JSONB.valueOf(json.writeValueAsString(parseIssues))
        it.status = status.name
        it.issues = JSONB.valueOf(json.writeValueAsString(issues))
        it.createdEmploymentId = createdEmploymentId
    }

fun EmployeeImportAttemptsRecord.toImportAttempt() =
    EmployeeImportAttempt(jobId, EmployeeImportPhase.valueOf(phase), actorId, createdAt.toInstant())

fun EmployeeImportAttempt.toRow(company: UUID, id: UUID) =
    EmployeeImportAttemptsRecord().also {
        it.companyId = company
        it.importId = id
        it.jobId = jobId
        it.phase = phase.name
        it.actorId = actorId
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
    }
