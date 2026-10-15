package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.Instant
import java.util.UUID

fun validateLeaveCarryBucket(bucket: LeaveCarryBucket): Result<Unit> {
    val parts =
        listOf(
            bucket.availableHalfDays,
            bucket.reservedHalfDays,
            bucket.consumedHalfDays,
            bucket.expiredHalfDays,
            bucket.transferredHalfDays,
            bucket.deductedHalfDays,
        )
    if (
        bucket.year !in 1901..2200 ||
            bucket.initialHalfDays !in 1..732 ||
            bucket.version !in 0..1000000000L ||
            parts.any { it !in 0..732 } ||
            parts.sumOf { it.toLong() } != bucket.initialHalfDays.toLong() ||
            bucket.validity?.through?.year?.let { it != bucket.year } == true
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
    return Result.Success(Unit)
}

/** A carry bucket is a subset of one main account, including its retained reservations. */
fun validateLeaveCarryBalance(balance: LeaveBalance, bucket: LeaveCarryBucket): Result<Unit> {
    val valid = validateLeaveCarryBucket(bucket)
    if (valid is Result.Failed) return valid
    if (
        bucket.year != balance.year ||
            bucket.availableHalfDays > balance.availableHalfDays ||
            bucket.reservedHalfDays > balance.reservedHalfDays ||
            bucket.consumedHalfDays > balance.consumedHalfDays
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
    return Result.Success(Unit)
}

/** Pure funding plan for already charged work dates; repositories acquire the owned snapshots. */
fun planLeaveCarryReservation(
    days: List<LeaveDay>,
    balances: List<LeaveBalance>,
    buckets: List<LeaveCarryBucket>,
    now: Instant,
): Result<List<LeaveCarryAllocation>> {
    val dates = validateLeaveDateSelection(days.map { RequestedLeaveDay(it.workDate, it.portion) })
    if (dates is Result.Failed) return dates
    val years = days.map { it.workDate.year }.toSet()
    if (
        balances.size !in 1..2 ||
            buckets.size > 2 ||
            balances.map { it.year }.toSet() != years ||
            balances.size != years.size ||
            buckets.map { it.year }.toSet().size != buckets.size ||
            buckets.map { it.id }.toSet().size != buckets.size ||
            buckets.any { it.year !in years }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
    val ordinary = mutableMapOf<Int, Int>()
    for (balance in balances) {
        if (balance.closed) return Result.Failed(Failure(FailureKind.CONFLICT, "leave_year_closed"))
        if (
            balance.availableHalfDays < 0 ||
                balance.reservedHalfDays < 0 ||
                balance.consumedHalfDays < 0
        )
            return Result.Failed(Failure(FailureKind.CONFLICT, "leave_ledger_conflict"))
        val bucket = buckets.singleOrNull { it.year == balance.year }
        if (bucket != null) {
            val checked = validateLeaveCarryBalance(balance, bucket)
            if (checked is Result.Failed) return checked
        }
        ordinary[balance.year] = balance.availableHalfDays - (bucket?.availableHalfDays ?: 0)
    }
    val remaining = buckets.associate { it.id to it.availableHalfDays }.toMutableMap()
    val allocated = mutableMapOf<UUID, Int>()
    for (day in days.sortedBy { it.workDate }) {
        var required = day.portion.halfDays
        val bucket = buckets.singleOrNull { it.year == day.workDate.year }
        if (
            bucket != null &&
                (bucket.validity == null ||
                    (now.isBefore(bucket.validity.expiresAt) &&
                        !day.workDate.isAfter(bucket.validity.through)))
        ) {
            val amount = minOf(required, remaining.getValue(bucket.id))
            remaining[bucket.id] = remaining.getValue(bucket.id) - amount
            required -= amount
            if (amount > 0) allocated[bucket.id] = (allocated[bucket.id] ?: 0) + amount
        }
        if (ordinary.getValue(day.workDate.year) < required)
            return Result.Failed(Failure(FailureKind.CONFLICT, "insufficient_leave_balance"))
        ordinary[day.workDate.year] = ordinary.getValue(day.workDate.year) - required
    }
    return Result.Success(
        java.util.List.copyOf(
            buckets
                .sortedBy { it.year }
                .mapNotNull { bucket ->
                    allocated[bucket.id]?.let { LeaveCarryAllocation(bucket.id, bucket.year, it) }
                }
        )
    )
}

/**
 * Arithmetic only; the use case owns the request transition, idempotency and ledger transaction.
 */
fun applyLeaveCarryMovement(
    bucket: LeaveCarryBucket,
    movement: LeaveCarryMovement,
): Result<LeaveCarryBucket> {
    val valid = validateLeaveCarryBucket(bucket)
    if (valid is Result.Failed) return valid
    if (
        movement.bucketId != bucket.id ||
            movement.halfDays !in 1..732 ||
            bucket.version >= 1000000000L
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
    val effect = movement.effect
    val updated =
        bucket.copy(
            availableHalfDays = bucket.availableHalfDays + movement.halfDays * effect.available,
            reservedHalfDays = bucket.reservedHalfDays + movement.halfDays * effect.reserved,
            consumedHalfDays = bucket.consumedHalfDays + movement.halfDays * effect.consumed,
            expiredHalfDays = bucket.expiredHalfDays + movement.halfDays * effect.expired,
            transferredHalfDays =
                bucket.transferredHalfDays + movement.halfDays * effect.transferred,
            deductedHalfDays = bucket.deductedHalfDays + movement.halfDays * effect.deducted,
            version = bucket.version + 1,
        )
    return validateLeaveCarryBucket(updated).map { updated }
}

fun planLeaveCarryExpiry(bucket: LeaveCarryBucket, now: Instant): Result<LeaveCarryMovement?> {
    val valid = validateLeaveCarryBucket(bucket)
    if (valid is Result.Failed) return valid
    return Result.Success(
        if (
            bucket.validity != null &&
                !now.isBefore(bucket.validity.expiresAt) &&
                bucket.availableHalfDays > 0
        )
            LeaveCarryMovement(bucket.id, LeaveCarryEffect.EXPIRE, bucket.availableHalfDays)
        else null
    )
}

/**
 * A valid reservation can finish after its deadline; returned units cannot regain expired validity.
 */
fun planLeaveCarryCompletion(
    bucket: LeaveCarryBucket,
    effect: LeaveBalanceEffect,
    halfDays: Int,
    now: Instant,
): Result<List<LeaveCarryMovement>> {
    val kind =
        when (effect) {
            LeaveBalanceEffect.CONSUME -> LeaveCarryEffect.CONSUME
            LeaveBalanceEffect.RELEASE -> LeaveCarryEffect.RELEASE
            LeaveBalanceEffect.REFUND -> LeaveCarryEffect.REFUND
            LeaveBalanceEffect.RESERVE ->
                return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
        }
    val movement = LeaveCarryMovement(bucket.id, kind, halfDays)
    return applyLeaveCarryMovement(bucket, movement).flatMap { updated ->
        planLeaveCarryExpiry(updated, now).flatMap { expiry ->
            if (expiry == null) Result.Success(java.util.List.of(movement))
            else
                applyLeaveCarryMovement(updated, expiry).map { java.util.List.of(movement, expiry) }
        }
    }
}

/** Completes the original funding across a request's years without acquiring or persisting data. */
fun planLeaveCarryRequestCompletion(
    expectedAllocations: Int,
    allocations: List<LeaveCarryAllocation>,
    buckets: List<LeaveCarryBucket>,
    effect: LeaveBalanceEffect,
    now: Instant,
): Result<List<LeaveCarryPostingPlan>> {
    if (
        effect == LeaveBalanceEffect.RESERVE ||
            expectedAllocations !in 0..2 ||
            allocations.size != expectedAllocations ||
            buckets.size != expectedAllocations ||
            allocations.map { it.bucketId }.toSet().size != allocations.size ||
            allocations.map { it.year }.toSet().size != allocations.size ||
            buckets.map { it.id }.toSet().size != buckets.size ||
            buckets.map { it.year }.toSet().size != buckets.size
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
    val plans = mutableListOf<LeaveCarryPostingPlan>()
    for (allocation in allocations.sortedBy { it.year }) {
        val bucket =
            buckets.singleOrNull { it.id == allocation.bucketId && it.year == allocation.year }
                ?: return Result.Failed(Failure(FailureKind.CONFLICT, "leave_carry_conflict"))
        val completed = planLeaveCarryCompletion(bucket, effect, allocation.halfDays, now)
        if (completed is Result.Failed) return completed
        plans +=
            (completed as Result.Success).value.mapIndexed { index, movement ->
                LeaveCarryPostingPlan(movement, bucket.year, bucket.version + index)
            }
    }
    return Result.Success(java.util.List.copyOf(plans))
}
