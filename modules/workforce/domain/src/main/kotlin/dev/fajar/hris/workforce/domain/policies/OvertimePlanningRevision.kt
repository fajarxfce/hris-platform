package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.workforce.domain.entities.ScheduledDay
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

/** Optimistic comparison of reviewed facts; it grants no access or reservation. */
fun overtimePlanningRevision(
    companyId: UUID,
    employeeId: UUID,
    employmentVersion: Long,
    timezone: String,
    day: ScheduledDay,
): String {
    val fields = buildList {
        addAll(
            listOf(
                "hris-overtime-planning-v1",
                companyId.toString(),
                employeeId.toString(),
                employmentVersion.toString(),
                timezone,
                day.workDate.toString(),
            )
        )
        when (day) {
            is ScheduledDay.Work ->
                addAll(
                    listOf(
                        "WORK",
                        day.origin.name,
                        day.originVersion.toString(),
                        day.shift.id.toString(),
                        day.shift.revision.toString(),
                        day.startsAt.toString(),
                        day.endsAt.toString(),
                        day.plannedMinutes.toString(),
                    )
                )
            is ScheduledDay.Off ->
                addAll(
                    listOf(
                        "OFF",
                        day.origin.name,
                        day.originVersion.toString(),
                        day.holidayId?.toString() ?: "",
                    )
                )
            is ScheduledDay.Unassigned -> add("UNASSIGNED")
        }
    }
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(fields.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        )
}
