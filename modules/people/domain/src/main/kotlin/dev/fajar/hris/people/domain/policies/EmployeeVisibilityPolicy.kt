package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.EmployeeVisibility

fun canReadOwnEmployment(permissions: Set<String>): Boolean =
    "people.self.read" in permissions || "people.read" in permissions

fun employeeVisibility(permissions: Set<String>): Result<EmployeeVisibility> =
    when {
        "people.read" in permissions -> Result.Success(EmployeeVisibility.ALL)
        "people.team.read" in permissions && "people.self.read" in permissions ->
            Result.Success(EmployeeVisibility.TEAM_AND_SELF)
        "people.team.read" in permissions -> Result.Success(EmployeeVisibility.TEAM)
        "people.self.read" in permissions -> Result.Success(EmployeeVisibility.SELF)
        else -> Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
    }
