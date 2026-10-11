package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.http.response
import dev.fajar.hris.people.delivery.responses.CompanyDirectoryResponse
import dev.fajar.hris.people.delivery.responses.DirectoryEmployeeResponse
import dev.fajar.hris.people.domain.usecases.ListCompanyDirectory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/directory")
class CompanyDirectoryController(private val list: ListCompanyDirectory) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): CompanyDirectoryResponse =
        list.execute(actor, query, after, limit).response().let {
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
}
