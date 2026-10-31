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
        "collaboration" to CompanyModule.COLLABORATION,
        "reporting" to CompanyModule.REPORTING,
    )

fun requestedSyncModules(request: HttpServletRequest, actor: Actor): Result<Set<CompanyModule>> {
    val values = request.getParameterValues("collections")
    val maximumCollections = SyncCollection.entries.size
    val maximumLength = SyncCollection.entries.sumOf { it.name.length + 1 }
    if (
        values != null &&
            (values.size > maximumCollections || values.sumOf { it.length } > maximumLength)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_sync_collections"))
    val tokens = values?.flatMap { it.split(',').map(String::trim) }?.filter { it.isNotEmpty() }
    if (tokens != null && tokens.size > maximumCollections)
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
                    SyncCollection.EXPENSE_CLAIMS,
                    SyncCollection.EXPENSE_PAYMENTS -> CompanyModule.EXPENSES
                    SyncCollection.LEAVE_REQUESTS,
                    SyncCollection.LEAVE_BALANCES -> CompanyModule.LEAVE
                    SyncCollection.OVERTIME_REQUESTS,
                    SyncCollection.ATTENDANCE_DAYS,
                    SyncCollection.WORK_SCHEDULES,
                    SyncCollection.ROSTER_DAYS,
                    SyncCollection.WORK_HOLIDAYS -> CompanyModule.WORKFORCE
                    SyncCollection.PAYSLIPS,
                    SyncCollection.PAYROLL_PAYMENTS -> CompanyModule.PAYROLL
                    SyncCollection.INBOX -> CompanyModule.COMMUNICATIONS
                    SyncCollection.DIRECTORY_EMPLOYEES,
                    SyncCollection.SELF_PROFILES -> CompanyModule.PEOPLE
                    SyncCollection.DOCUMENT_EVIDENCE -> CompanyModule.DOCUMENTS
                }
            }
            .toSet()
    }
}
