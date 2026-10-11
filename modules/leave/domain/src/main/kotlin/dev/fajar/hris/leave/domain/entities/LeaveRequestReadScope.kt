package dev.fajar.hris.leave.domain.entities

/** Self-service never expands to HR or delegated-approver access. */
enum class LeaveRequestReadScope {
    AUTHORIZED,
    SELF,
}
