package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.http.response
import dev.fajar.hris.people.delivery.responses.CompanyDirectoryResponse
import dev.fajar.hris.people.delivery.responses.DirectoryDepartmentResponse
import dev.fajar.hris.people.delivery.responses.DirectoryDepartmentsResponse
import dev.fajar.hris.people.delivery.responses.DirectoryEmployeeResponse
import dev.fajar.hris.people.delivery.responses.DirectoryRecordResponse
import dev.fajar.hris.people.domain.usecases.FindDirectoryDepartments
import dev.fajar.hris.people.domain.usecases.GetDirectoryRecord
import dev.fajar.hris.people.domain.usecases.ListCompanyDirectory
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/directory")
class CompanyDirectoryController(
    private val list: ListCompanyDirectory,
    private val findDepartments: FindDirectoryDepartments,
    private val getRecord: GetDirectoryRecord,
) {
    @GetMapping("/{id}")
    fun record(actor: Actor, @PathVariable id: UUID): DirectoryRecordResponse =
        getRecord.execute(actor, id).response().let { record ->
            val employee = requireNotNull(record.employee)
            DirectoryRecordResponse(
                record.id,
                record.version,
                employee.employeeNumber,
                employee.name,
                employee.positionId,
                employee.position,
                employee.departmentId,
                employee.department,
            )
        }

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
        @RequestParam(required = false) departmentId: UUID?,
    ): CompanyDirectoryResponse =
        list.execute(actor, query, after, limit, departmentId).response().let {
            CompanyDirectoryResponse(
                it.asOf,
                it.timezone,
                Page(
                    it.employees.items.map { employee ->
                        DirectoryEmployeeResponse(
                            employee.id,
                            employee.employeeNumber,
                            employee.name,
                            employee.position,
                            employee.department,
                        )
                    },
                    it.employees.nextCursor,
                ),
            )
        }

    @GetMapping("/departments")
    fun departments(
        actor: Actor,
        @RequestParam(defaultValue = "") query: String,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): DirectoryDepartmentsResponse =
        findDepartments.execute(actor, query, limit).response().let {
            DirectoryDepartmentsResponse(
                it.items.map { row -> DirectoryDepartmentResponse(row.id, row.name) },
                it.hasMore,
            )
        }
}
