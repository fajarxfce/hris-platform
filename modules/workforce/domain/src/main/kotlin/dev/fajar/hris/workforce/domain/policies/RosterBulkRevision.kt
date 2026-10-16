package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.workforce.domain.entities.*
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

/** A comparison of reviewed content, never an authorization token or reservation. */
fun rosterBulkReview(
    companyId: UUID,
    selection: RosterBulkSelection,
    employees: List<RosterBulkEmployee>,
): RosterBulkReview {
    val fields = buildList {
        add("hris-roster-bulk-v1")
        add(companyId.toString())
        add(selection.mode.name)
        add(selection.shift?.id?.toString() ?: "")
        add(selection.shift?.version?.toString() ?: "")
        for (entry in employees) {
            add(entry.employee.id.toString())
            add(entry.employee.version.toString())
            for (day in entry.days) {
                add(day.workDate.toString())
                add(day.expectedVersion?.toString() ?: "")
                add(day.overrideActive.toString())
                add(day.changed.toString())
                addAll(rosterBulkDayFields(day.before))
                addAll(rosterBulkDayFields(day.after))
            }
        }
    }
    val digest = MessageDigest.getInstance("SHA-256")
    for (field in fields) {
        val bytes = field.toByteArray(Charsets.UTF_8)
        digest.update("${bytes.size}:".toByteArray(Charsets.US_ASCII))
        digest.update(bytes)
    }
    return RosterBulkReview(
        selection,
        employees.toList(),
        HexFormat.of().formatHex(digest.digest()),
        employees.sumOf { it.days.count { day -> day.changed } },
    )
}

private fun rosterBulkDayFields(day: ScheduledDay): List<String> =
    when (day) {
        is ScheduledDay.Work ->
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
        is ScheduledDay.Off ->
            listOf(
                "OFF",
                day.origin.name,
                day.originVersion.toString(),
                day.holidayId?.toString() ?: "",
            )
        is ScheduledDay.Unassigned -> listOf("UNASSIGNED")
    }
