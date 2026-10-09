package dev.fajar.hris.availability

import dev.fajar.hris.administration.domain.entities.CompanyModule
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.domain.entities.SyncCollection
import dev.fajar.hris.sync.domain.policies.selectSyncCollections
import jakarta.servlet.http.HttpServletRequest

val companyPolicyExemptPackages: Set<String> =
    setOf("administration", "identity", "organization", "core")

/** The application composition maps feature adapters to their company capabilities. */
val companyModulePackages: Map<String, CompanyModule> =
    mapOf(
        "people" to CompanyModule.PEOPLE,
        "workforce" to CompanyModule.WORKFORCE,
        "leave" to CompanyModule.LEAVE,
        "expenses" to CompanyModule.EXPENSES,
        "payroll" to CompanyModule.PAYROLL,
        "documents" to CompanyModule.DOCUMENTS,
        "communications" to CompanyModule.COMMUNICATIONS,
        "reporting" to CompanyModule.REPORTING,
    )

fun requestedSyncModules(request: HttpServletRequest, actor: Actor): Result<Set<CompanyModule>> {
    val values = request.getParameterValues("collections")
    if (values != null && (values.size > 7 || values.sumOf { it.length } > 200))
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_sync_collections"))
    val tokens = values?.flatMap { it.split(',').map(String::trim) }?.filter { it.isNotEmpty() }
    if (tokens != null && tokens.size > 7)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_sync_collections"))
    if (tokens?.any { name -> SyncCollection.entries.none { it.name == name } } == true)
        throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST
        )
    val selected = tokens?.map { SyncCollection.valueOf(it) }?.toSet()
    return selectSyncCollections(actor.permissions, selected).map { collections ->
        collections
            .map { collection ->
                when (collection) {
                    SyncCollection.EXPENSE_CLAIMS -> CompanyModule.EXPENSES
                    SyncCollection.LEAVE_REQUESTS,
                    SyncCollection.LEAVE_BALANCES -> CompanyModule.LEAVE
                    SyncCollection.OVERTIME_REQUESTS -> CompanyModule.WORKFORCE
                    SyncCollection.PAYSLIPS,
                    SyncCollection.PAYROLL_PAYMENTS -> CompanyModule.PAYROLL
                    SyncCollection.INBOX -> CompanyModule.COMMUNICATIONS
                }
            }
            .toSet()
    }
}
