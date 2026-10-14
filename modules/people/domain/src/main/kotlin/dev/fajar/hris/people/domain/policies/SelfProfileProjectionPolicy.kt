package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.LocalDate
import java.time.ZoneId

fun canReadSelfProfile(permissions: Set<String>) =
    "people.self.read" in permissions ||
        ("people.read" in permissions && "people.profile.read" in permissions)

fun projectSelfProfile(
    candidate: SelfProfileCandidate,
    current: SelfProfileRecord?,
    today: LocalDate,
    zone: ZoneId,
): Result<SelfProfileProjection> {
    val employee = candidate.employee?.takeIf { it.person.accountId != null }
    val snapshot =
        employee?.let {
            SelfProfileSnapshot(
                EmploymentDetails(
                    today,
                    it.copy(managerAccountId = null),
                    candidate.branch,
                    candidate.department,
                    candidate.position,
                    candidate.costCenter,
                    candidate.manager?.let { manager ->
                        EmploymentManagerReference(
                            manager.id,
                            manager.employeeNumber,
                            manager.legalName,
                            manager.status in
                                setOf(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION) &&
                                manager.startDate <= today &&
                                manager.endDate?.isBefore(today) != true,
                        )
                    },
                ),
                candidate.ownerCompanyId,
                candidate.profileVersion,
                zone.id,
            )
        }
    val manager = candidate.manager
    val nextDate =
        listOfNotNull(
                candidate.nextRevision,
                candidate.nextManagerRevision,
                manager?.startDate,
                manager?.endDate?.takeIf { it < LocalDate.MAX }?.plusDays(1),
            )
            .filter { it.isAfter(today) }
            .minOrNull()
    val next = nextDate?.atStartOfDay(zone)?.toInstant()
    // The recorded date is provenance, not a reason to rewrite unchanged personal data daily.
    val preceding = current?.snapshot?.let { it.copy(details = it.details.copy(asOf = today)) }
    if (preceding == snapshot) return Result.Success(SelfProfileProjection(null, next))
    if (current?.version == Long.MAX_VALUE)
        return Result.Failed(Failure(FailureKind.CONFLICT, "profile_revision_exhausted"))
    return Result.Success(
        SelfProfileProjection(
            SelfProfileRecord(candidate.id, current?.version?.plus(1) ?: 0, snapshot),
            next,
        )
    )
}
