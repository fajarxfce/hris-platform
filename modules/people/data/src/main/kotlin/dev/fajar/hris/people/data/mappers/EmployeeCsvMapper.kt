package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.people.data.models.*
import dev.fajar.hris.people.domain.entities.*
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

fun EmployeeCsvDocument.toImportRows(): List<EmployeeImportRow> {
    require(
        headers.toSet().containsAll(requiredEmployeeCsvColumns) &&
            employeeCsvColumns.containsAll(headers)
    )
    return rows.mapIndexed { index, values ->
        val cells = headers.zip(values).toMap()
        val issues = linkedMapOf<String, String>()
        val start = parseEmployeeImportDate(cells["start_date"], "startDate", true, issues)
        val end = parseEmployeeImportDate(cells["end_date"], "endDate", false, issues)
        val birth = parseEmployeeImportDate(cells["birth_date"], "birthDate", false, issues)
        val manager = parseEmployeeImportId(cells["manager_id"], "managerId", issues)
        val branch = parseEmployeeImportId(cells["branch_id"], "branchId", issues)
        val department = parseEmployeeImportId(cells["department_id"], "departmentId", issues)
        val position = parseEmployeeImportId(cells["position_id"], "positionId", issues)
        val costCenter = parseEmployeeImportId(cells["cost_center_id"], "costCenterId", issues)
        val contract =
            ContractKind.entries.firstOrNull { it.name == cells["contract"]?.trim()?.uppercase() }
        if (contract == null) issues["contract"] = "invalid_contract"
        val number = cells.getValue("employee_number")
        val name = cells.getValue("legal_name")
        val draft =
            if (issues.isNotEmpty()) null
            else
                EmployeeDraft(
                    UUID.randomUUID(),
                    number,
                    PersonProfile(
                        UUID.randomUUID(),
                        null,
                        name,
                        birth,
                        cells.getValue("nationality"),
                        cells["email"]?.takeIf { it.isNotBlank() },
                    ),
                    EmploymentTerms(
                        requireNotNull(start),
                        requireNotNull(contract),
                        start,
                        end,
                        EmploymentStatus.ACTIVE,
                        branch,
                        department,
                        position,
                        costCenter,
                        manager,
                    ),
                )
        EmployeeImportRow(index + 1, number, name, draft, issues.toMap())
    }
}

fun parseEmployeeImportDate(
    value: String?,
    field: String,
    required: Boolean,
    issues: MutableMap<String, String>,
): LocalDate? {
    if (value.isNullOrBlank()) {
        if (required) issues[field] = "date_required"
        return null
    }
    return try {
        LocalDate.parse(value.trim())
    } catch (error: DateTimeParseException) {
        issues[field] = "invalid_date"
        null
    }
}

fun parseEmployeeImportId(
    value: String?,
    field: String,
    issues: MutableMap<String, String>,
): UUID? {
    if (value.isNullOrBlank()) return null
    val normalized = value.trim()
    if (
        !normalized.matches(
            Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
        )
    ) {
        issues[field] = "invalid_id"
        return null
    }
    return UUID.fromString(normalized)
}
