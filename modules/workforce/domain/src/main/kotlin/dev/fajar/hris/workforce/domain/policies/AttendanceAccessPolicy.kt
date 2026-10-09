package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.people.domain.entities.Employee
import dev.fajar.hris.people.domain.entities.EmploymentStatus

fun canReviewAttendance(
    actor: Actor,
    current: Employee?,
    ownerAccountId: java.util.UUID?,
): Boolean =
    actor.accountId != ownerAccountId &&
        actor.accountId != current?.person?.accountId &&
        ("attendance.verify" in actor.permissions ||
            ("attendance.team.verify" in actor.permissions &&
                current?.managerAccountId == actor.accountId &&
                current.terms.status != EmploymentStatus.ENDED))
