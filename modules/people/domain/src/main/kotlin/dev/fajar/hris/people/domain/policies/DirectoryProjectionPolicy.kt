package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DirectoryProjection(val changed: DirectoryRecord?, val nextRefresh: Instant?)

fun projectDirectoryEmployee(
    candidate: DirectoryCandidate,
    current: DirectoryRecord?,
    today: LocalDate,
    zone: ZoneId,
): Result<DirectoryProjection> {
    val eligible =
        candidate.status in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION) &&
            candidate.start?.let { !it.isAfter(today) } == true &&
            candidate.end?.isBefore(today) != true
    val entry = candidate.entry.takeIf { eligible }
    val nextDate =
        listOfNotNull(
                candidate.nextRevision,
                candidate.start,
                candidate.end?.takeIf { it < LocalDate.MAX }?.plusDays(1),
            )
            .filter { it.isAfter(today) }
            .minOrNull()
    val next = nextDate?.atStartOfDay(zone)?.toInstant()
    if (entry == current?.employee) return Result.Success(DirectoryProjection(null, next))
    if (current?.version == Long.MAX_VALUE)
        return Result.Failed(Failure(FailureKind.CONFLICT, "directory_revision_exhausted"))
    return Result.Success(
        DirectoryProjection(
            DirectoryRecord(candidate.id, current?.version?.plus(1) ?: 0, entry),
            next,
        )
    )
}
