package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.workforce.domain.entities.CalendarOrigin
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired

/** Seeds retained carry awards for storage, request and expiry integration tests. */
abstract class LeaveCarryStorageFixture : LeaveAccountingApiFixture() {
    @Autowired protected lateinit var carryExpiryJobs: JobRepository
    @Autowired protected lateinit var carryExpiries: LeaveCarryExpiryRepository
    @Autowired protected lateinit var carryRepository: LeaveCarryRepository
    @Autowired protected lateinit var carryLedger: LeaveLedgerRepository
    @Autowired protected lateinit var carryEntitlements: LeaveEntitlementRepository
    @Autowired protected lateinit var carryPolicies: LeavePolicyRepository
    @Autowired protected lateinit var carryRequests: LeaveRequestRepository
    @Autowired protected lateinit var carryApprovals: ApprovalRepository

    protected data class PreparedCarry(
        val f: LeaveFixture,
        val closing: LeaveYearClosing,
        val award: LeaveCarryAward?,
    )

    protected val carryDeadline: Instant = Instant.parse("2027-03-31T17:00:00Z")

    protected fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun prepareCarry(
        months: Int? = 3,
        at: Instant = Instant.parse("2027-01-05T03:00:00Z"),
    ): PreparedCarry {
        val f = accountingFixture(at, carry = "3")
        accountingBody(adjust(f, "3"))
        val actor = accountingActor(f)
        return success(
            transactions.run(actor) {
                success(carryLedger.lock(f.company, f.employee))
                success(carryPolicies.lock(f.company))
                val original = requireNotNull(success(carryPolicies.find(f.company, f.type)))
                val policy =
                    original.policy.copy(
                        accrual =
                            requireNotNull(original.policy.accrual)
                                .copy(carryValidityMonths = months)
                    )
                success(
                    carryPolicies.save(
                        actor,
                        original.copy(policy = policy),
                        original.version,
                        "Carry validity fixture",
                    )
                )
                val type = requireNotNull(success(carryPolicies.find(f.company, f.type)))
                val balance = success(carryLedger.balance(f.company, f.employee, f.type, 2026))
                val validity = success(leaveCarryValidity(2027, months, "Asia/Jakarta"))
                val amount = if (validity != null && !at.isBefore(validity.expiresAt)) 0 else 6
                val closing =
                    LeaveYearClosing(
                        UUID.randomUUID(),
                        f.employee,
                        f.type,
                        2026,
                        balance.version,
                        balance.availableHalfDays,
                        balance.consumedHalfDays,
                        0,
                        LeaveYearRollover(amount, 6 - amount),
                        LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                        actor.accountId,
                        at,
                        "Year-end carry fixture",
                        true,
                        "Asia/Jakarta",
                    )
                val award =
                    if (amount == 0) null
                    else
                        LeaveCarryAward(
                            LeaveCarryBucket(closing.id, 2027, amount, amount, validity = validity),
                            f.employee,
                            f.type,
                            "Asia/Jakarta",
                            months,
                        )
                Result.Success(PreparedCarry(f, closing, award))
            }
        )
    }

    protected fun storeCarry(
        prepared: PreparedCarry,
        includeAward: Boolean = true,
        includeExpiry: Boolean = true,
    ): Result<Unit> {
        val (f, closing, award) = prepared
        val entries = buildList {
            if (closing.rollover.carryHalfDays > 0) {
                add(
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        f.employee,
                        f.type,
                        2026,
                        LeaveLedgerKind.CARRY_OUT,
                        closing.id,
                        null,
                        -closing.rollover.carryHalfDays,
                        0,
                        0,
                        closing.actorId,
                        closing.recordedAt,
                        closing.reason,
                    )
                )
                add(
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        f.employee,
                        f.type,
                        2027,
                        LeaveLedgerKind.CARRY_IN,
                        closing.id,
                        null,
                        closing.rollover.carryHalfDays,
                        0,
                        0,
                        closing.actorId,
                        closing.recordedAt,
                        closing.reason,
                    )
                )
            }
            if (closing.rollover.expireHalfDays > 0)
                add(
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        f.employee,
                        f.type,
                        2026,
                        LeaveLedgerKind.EXPIRE,
                        closing.id,
                        null,
                        -closing.rollover.expireHalfDays,
                        0,
                        0,
                        closing.actorId,
                        closing.recordedAt,
                        closing.reason,
                    )
                )
        }
        return transactions.run(accountingActor(f)) {
            carryLedger
                .lock(f.company, f.employee)
                .flatMap { carryEntitlements.createClosing(f.company, closing) }
                .flatMap {
                    if (includeAward && award != null) carryRepository.create(f.company, award)
                    else Result.Success(Unit)
                }
                .flatMap {
                    val validity = award?.bucket?.validity
                    if (!includeExpiry || validity == null) Result.Success(Unit)
                    else {
                        val actor = accountingActor(f)
                        val expiry =
                            LeaveCarryExpiry(
                                UUID.randomUUID(),
                                LeaveCarryExpirySource.Closing(closing.id, f.employee),
                                f.type,
                                closing.year + 1,
                                requireNotNull(closing.timezone),
                                validity,
                                closing.actorId,
                                closing.recordedAt,
                                closing.reason,
                                1,
                                UUID.randomUUID(),
                            )
                        val job =
                            JobRequest(
                                expiry.jobId,
                                f.company,
                                closing.actorId,
                                JobKind.LEAVE_CARRY_EXPIRY,
                                UUID.randomUUID(),
                                mapOf("expiryId" to expiry.id.toString()),
                                actor.authenticatedAt,
                                actor.credentialVersion ?: 0,
                                actor.correlationId,
                                closing.recordedAt,
                                2,
                                scheduledFor = validity.expiresAt,
                            )
                        carryExpiryJobs.create(job).flatMap {
                            carryExpiries.create(
                                f.company,
                                expiry,
                                LeaveCarryExpiryAttempt(
                                    job.id,
                                    1,
                                    0,
                                    closing.recordedAt,
                                    closing.reason,
                                ),
                            )
                        }
                    }
                }
                .flatMap { carryLedger.append(f.company, entries) }
                .flatMap {
                    carryEntitlements.closeAccount(
                        f.company,
                        f.employee,
                        f.type,
                        2026,
                        closing.sourceVersion + entries.count { it.year == 2026 },
                        closing.id,
                    )
                }
        }
    }

    protected fun carried(months: Int? = 3): PreparedCarry =
        prepareCarry(months).also { success(storeCarry(it)) }

    protected fun storedBucket(c: PreparedCarry): LeaveCarryBucket =
        success(
            transactions.run(accountingActor(c.f)) {
                carryLedger
                    .lock(c.f.company, c.f.employee, shared = true)
                    .flatMap {
                        carryRepository.buckets(c.f.company, c.f.employee, c.f.type, setOf(2027))
                    }
                    .map { it.single() }
            }
        )

    protected fun storedBalance(c: PreparedCarry): LeaveBalance =
        success(
            transactions.run(accountingActor(c.f)) {
                carryLedger.lock(c.f.company, c.f.employee, shared = true).flatMap {
                    carryLedger.balance(c.f.company, c.f.employee, c.f.type, 2027)
                }
            }
        )

    protected fun expiry(
        c: PreparedCarry,
        at: Instant = carryDeadline,
        ledgerIncluded: Boolean = true,
        bucket: LeaveCarryBucket = storedBucket(c),
        afterLock: () -> Unit = {},
    ): Result<Unit> {
        val movement = requireNotNull(success(planLeaveCarryExpiry(bucket, carryDeadline)))
        val posting =
            LeaveCarryPosting(UUID.randomUUID(), movement, bucket.version, UUID.randomUUID())
        val entry =
            LeaveLedgerEntry(
                posting.ledgerEntryId,
                c.f.employee,
                c.f.type,
                bucket.year,
                LeaveLedgerKind.EXPIRE,
                posting.id,
                null,
                -movement.halfDays,
                0,
                0,
                c.closing.actorId,
                at,
                "Carry validity ended",
                posting.id,
            )
        return transactions.run(accountingActor(c.f)) {
            carryLedger
                .lock(c.f.company, c.f.employee)
                .flatMap {
                    afterLock()
                    carryRepository.append(c.f.company, listOf(posting))
                }
                .flatMap {
                    if (ledgerIncluded) carryLedger.append(c.f.company, listOf(entry))
                    else Result.Success(Unit)
                }
        }
    }

    protected fun reserveCarry(
        c: PreparedCarry,
        date: LocalDate = LocalDate.of(2027, 3, 15),
        units: Int = 2,
        allocationsIncluded: Boolean = true,
        ledgerIncluded: Boolean = true,
        fundingCount: Int = 1,
    ): Result<UUID> {
        val f = c.f
        val bucket = storedBucket(c)
        val id = UUID.randomUUID()
        val start = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val days =
            listOf(
                LeaveDay(
                    date,
                    LeavePortion.FULL,
                    start,
                    start.plusSeconds(28800),
                    480,
                    480,
                    UUID.randomUUID(),
                    0,
                    CalendarOrigin.PATTERN,
                    0,
                )
            )
        return transactions.run(accountingActor(f)) {
            success(carryLedger.lock(f.company, f.employee))
            val template =
                success(
                        carryApprovals.templates(
                            f.company,
                            ApprovalKind.LEAVE,
                            LocalDate.of(2027, 1, 5),
                        )
                    )
                    .single()
            val approval =
                ApprovalRequest(
                    UUID.randomUUID(),
                    ApprovalKind.LEAVE,
                    id,
                    f.account,
                    f.account,
                    template.id,
                    template.revision,
                    listOf(ApprovalStage(setOf(f.managerAccount))),
                    0,
                    ApprovalStatus.PENDING,
                    0,
                    c.closing.recordedAt,
                )
            val request =
                LeaveRequest(
                    id,
                    f.employee,
                    "CARRY-FIXTURE",
                    "Carry fixture",
                    f.account,
                    f.account,
                    c.closing.recordedAt,
                    c.closing.policy,
                    days,
                    "Carry-funded request",
                    LeaveStatus.PENDING,
                    approval.id,
                    null,
                    0,
                    carryAllocationCount = fundingCount,
                )
            val entry =
                LeaveLedgerEntry(
                    UUID.randomUUID(),
                    f.employee,
                    f.type,
                    2027,
                    LeaveLedgerKind.RESERVE,
                    id,
                    id,
                    -2,
                    2,
                    0,
                    f.account,
                    request.submittedAt,
                    request.reason,
                )
            val posting =
                LeaveCarryPosting(
                    UUID.randomUUID(),
                    LeaveCarryMovement(bucket.id, LeaveCarryEffect.RESERVE, units),
                    bucket.version,
                    entry.id,
                    id,
                )
            carryApprovals
                .create(f.company, approval)
                .flatMap { carryRequests.create(f.company, request) }
                .flatMap {
                    if (allocationsIncluded)
                        carryRepository.allocate(
                            f.company,
                            id,
                            listOf(LeaveCarryAllocation(bucket.id, 2027, units)),
                        )
                    else Result.Success(Unit)
                }
                .flatMap {
                    if (allocationsIncluded) carryRepository.append(f.company, listOf(posting))
                    else Result.Success(Unit)
                }
                .flatMap {
                    if (ledgerIncluded) carryLedger.append(f.company, listOf(entry))
                    else Result.Success(Unit)
                }
                .map { id }
        }
    }

    protected fun finishCarry(
        c: PreparedCarry,
        requestId: UUID,
        effect: LeaveBalanceEffect,
        at: Instant,
        settleReturned: Boolean = true,
    ): Result<Unit> {
        val f = c.f
        return transactions.run(accountingActor(f)) {
            success(carryLedger.lock(f.company, f.employee))
            val bucket =
                success(carryRepository.buckets(f.company, f.employee, f.type, setOf(2027)))
                    .single()
            val allocation = success(carryRepository.allocations(f.company, requestId)).single()
            val planned = success(planLeaveCarryCompletion(bucket, effect, allocation.halfDays, at))
            val changes =
                if (settleReturned) planned
                else planned.filter { it.effect != LeaveCarryEffect.EXPIRE }
            val postings = mutableListOf<LeaveCarryPosting>()
            val entries =
                changes.mapIndexed { index, movement ->
                    val posting =
                        LeaveCarryPosting(
                            UUID.randomUUID(),
                            movement,
                            bucket.version + index,
                            UUID.randomUUID(),
                            if (movement.effect == LeaveCarryEffect.EXPIRE) null else requestId,
                        )
                    postings += posting
                    val expire = movement.effect == LeaveCarryEffect.EXPIRE
                    val kind = if (expire) LeaveLedgerKind.EXPIRE else effect.kind
                    LeaveLedgerEntry(
                        posting.ledgerEntryId,
                        f.employee,
                        f.type,
                        2027,
                        kind,
                        if (expire) posting.id else requestId,
                        posting.requestId,
                        movement.halfDays * movement.effect.available,
                        movement.halfDays * movement.effect.reserved,
                        movement.halfDays * movement.effect.consumed,
                        c.closing.actorId,
                        at,
                        "Funding transition fixture",
                        if (expire) posting.id else null,
                    )
                }
            carryRepository.append(f.company, postings).flatMap {
                carryLedger.append(f.company, entries)
            }
        }
    }
}
