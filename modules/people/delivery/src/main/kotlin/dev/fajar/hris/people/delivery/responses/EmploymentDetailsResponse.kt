package dev.fajar.hris.people.delivery.responses

import java.time.LocalDate

data class EmploymentDetailsResponse(
    val asOf: LocalDate,
    val employee: EmployeeResponse,
    val branch: EmploymentUnitReferenceResponse?,
    val department: EmploymentUnitReferenceResponse?,
    val position: EmploymentUnitReferenceResponse?,
    val costCenter: EmploymentUnitReferenceResponse?,
    val manager: EmploymentManagerReferenceResponse?,
)
