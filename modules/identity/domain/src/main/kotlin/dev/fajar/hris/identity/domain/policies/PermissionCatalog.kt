package dev.fajar.hris.identity.domain.policies

object PermissionCatalog {
    val platformAdministrator = setOf("companies.create", "identity.manage")
    val companyAdministrator =
        setOf(
            "company.read",
            "company.manage",
            "identity.manage",
            "people.read",
            "people.manage",
            "people.profile.read",
            "people.profile.manage",
            "workforce.read",
            "workforce.manage",
            "workforce.close",
            "attendance.verify",
            "attendance.correct",
            "leave.read",
            "leave.manage",
            "leave.approve",
            "approvals.read",
            "approvals.manage",
            "expenses.read",
            "expenses.approve",
            "documents.read",
            "documents.manage",
            "announcements.read",
            "announcements.manage",
            "reports.read",
            "settings.manage",
            "audit.read",
            "jobs.read",
            "jobs.manage",
            "jobs.retry",
        )
    val humanResources =
        setOf(
            "company.read",
            "people.read",
            "people.manage",
            "people.profile.read",
            "people.profile.manage",
            "workforce.read",
            "workforce.manage",
            "workforce.close",
            "attendance.verify",
            "attendance.correct",
            "leave.read",
            "leave.manage",
            "leave.approve",
            "approvals.read",
            "documents.read",
            "documents.manage",
            "payroll.read",
            "payroll.calculate",
            "reports.read",
        )
    val finance =
        setOf(
            "company.read",
            "people.read",
            "expenses.read",
            "expenses.approve",
            "expenses.pay",
            "payroll.read",
            "payroll.review",
            "payroll.finalize",
            "payroll.pay",
            "approvals.read",
            "documents.read",
            "reports.read",
        )
    val manager =
        setOf(
            "company.read",
            "people.team.read",
            "workforce.team.read",
            "attendance.team.verify",
            "leave.team.read",
            "leave.team.approve",
            "expenses.team.read",
            "expenses.team.approve",
            "approvals.read",
        )
    val employee =
        setOf(
            "company.read",
            "people.self.read",
            "attendance.self.record",
            "leave.self.manage",
            "expenses.self.manage",
            "documents.self.read",
            "payroll.self.read",
        )
    val assignable = companyAdministrator + humanResources + finance + manager + employee
}
