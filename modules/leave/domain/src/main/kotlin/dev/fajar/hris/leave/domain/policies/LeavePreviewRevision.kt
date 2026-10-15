package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.leave.domain.entities.*
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

/** Content revision for optimistic review; this is not an authorization credential. */
fun leavePreviewRevision(
    companyId: UUID,
    employeeId: UUID,
    timezone: String,
    policy: LeavePolicySnapshot,
    selected: List<RequestedLeaveDay>,
    days: List<LeaveDay>,
    balances: List<LeavePreviewBalance>,
    carryBuckets: List<LeaveCarryBucket> = emptyList(),
    carryAllocations: List<LeaveCarryAllocation> = emptyList(),
): String {
    val fields = buildList {
        addAll(
            listOf(
                "hris-leave-preview-v1",
                companyId.toString(),
                employeeId.toString(),
                timezone,
                policy.typeId.toString(),
                policy.revision.toString(),
                "selected",
                selected.size.toString(),
            )
        )
        selected
            .sortedBy { it.workDate }
            .forEach { addAll(listOf(it.workDate.toString(), it.portion.name)) }
        addAll(listOf("charged", days.size.toString()))
        days
            .sortedBy { it.workDate }
            .forEach {
                addAll(
                    listOf(
                        it.workDate.toString(),
                        it.portion.name,
                        it.startsAt.toString(),
                        it.endsAt.toString(),
                        it.plannedMinutes.toString(),
                        it.chargedMinutes.toString(),
                        it.shiftId.toString(),
                        it.shiftRevision.toString(),
                        it.scheduleOrigin.name,
                        it.scheduleRevision.toString(),
                    )
                )
            }
        addAll(listOf("balances", balances.size.toString()))
        balances
            .sortedBy { it.balance.year }
            .forEach {
                addAll(
                    listOf(
                        it.balance.year.toString(),
                        it.balance.accountId?.toString() ?: "",
                        it.balance.version.toString(),
                        it.balance.availableHalfDays.toString(),
                        it.balance.reservedHalfDays.toString(),
                        it.balance.consumedHalfDays.toString(),
                        it.balance.closed.toString(),
                        it.requestedHalfDays.toString(),
                    )
                )
            }
        if (carryBuckets.isNotEmpty()) {
            addAll(listOf("carry", carryBuckets.size.toString()))
            carryBuckets
                .sortedBy { it.year }
                .forEach {
                    addAll(
                        listOf(
                            it.id.toString(),
                            it.year.toString(),
                            it.version.toString(),
                            it.availableHalfDays.toString(),
                            it.reservedHalfDays.toString(),
                            it.consumedHalfDays.toString(),
                            it.validity?.through?.toString() ?: "",
                            it.validity?.expiresAt?.toString() ?: "",
                        )
                    )
                }
            addAll(listOf("carry-funding", carryAllocations.size.toString()))
            carryAllocations
                .sortedBy { it.year }
                .forEach {
                    addAll(
                        listOf(it.bucketId.toString(), it.year.toString(), it.halfDays.toString())
                    )
                }
        }
    }
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(fields.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        )
}
