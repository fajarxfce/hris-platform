package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.usecases.StartLeaveYearCloseBatch
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class LeaveCarryExpiryAdmissionTest : LeaveCarryStorageFixture() {
    @Autowired private lateinit var startBatch: StartLeaveYearCloseBatch

    private fun fill(c: PreparedCarry, count: Int) {
        val actor = accountingActor(c.f)
        success(
            transactions.run(actor) {
                repeat(count) {
                    success(
                        carryExpiryJobs.create(
                            JobRequest(
                                UUID.randomUUID(),
                                c.f.company,
                                actor.accountId,
                                JobKind.DOCUMENT_INVENTORY,
                                UUID.randomUUID(),
                                emptyMap(),
                                actor.authenticatedAt,
                                actor.credentialVersion ?: 0,
                                actor.correlationId,
                                clock.instant(),
                                1,
                            )
                        )
                    )
                }
                Result.Success(Unit)
            }
        )
    }

    private fun start(
        c: PreparedCarry,
        id: UUID = UUID.randomUUID(),
        key: UUID = UUID.randomUUID(),
    ) =
        startBatch.execute(
            accountingActor(c.f),
            key,
            id,
            c.f.type,
            2026,
            c.closing.policy.revision,
            null,
            "Reviewed carry closing",
        )

    @Test
    fun aBatchReservesBothJobsOrCommitsNeither() {
        val c = prepareCarry()
        fill(c, 99)
        val key = UUID.randomUUID()
        val id = UUID.randomUUID()
        assertEquals("job_queue_full", (start(c, id, key) as Result.Failed).failure.code)
        assertEquals(99, accountingRows(c.f, "background_jobs"))
        assertEquals(0, accountingRows(c.f, "leave_batches"))
        assertEquals(0, accountingRows(c.f, "leave_carry_expiries"))
        database()
            .update(
                """update background_jobs set status='FAILED',finished_at=now()
            where id=(select id from background_jobs where company_id=? order by id limit 1)""",
                c.f.company,
            )
        success(start(c, id, key))
        assertEquals(
            100,
            success(
                transactions.run(accountingActor(c.f)) { carryExpiryJobs.pendingCount(c.f.company) }
            ),
        )
        assertEquals(1, accountingRows(c.f, "leave_carry_expiries"))
        assertTrue(success(start(c, id, key)).replayed)
        assertEquals(101, accountingRows(c.f, "background_jobs"))
    }

    @Test
    fun directClosingRollsBackWhenItsExpiryCannotBeAdmitted() {
        val c = prepareCarry()
        fill(c, 100)
        val counts =
            listOf(
                    "leave_year_closings",
                    "leave_ledger",
                    "leave_accounts",
                    "leave_carry_buckets",
                    "operation_receipts",
                    "mobile_sync_changes",
                )
                .associateWith { accountingRows(c.f, it) }
        val response =
            closeYear(
                c.f,
                closingBody(c.closing.id, c.closing.sourceVersion, c.closing.policy.revision),
            )
        assertEquals(409, response.statusCode(), response.body())
        assertTrue(response.body().contains("job_queue_full"))
        assertEquals(counts, counts.keys.associateWith { accountingRows(c.f, it) })
        assertEquals(0, accountingRows(c.f, "leave_carry_expiries"))
    }

    @Test
    fun admissionAfterTheFrozenDeadlineNeedsOnlyTheClosingJob() {
        val c = prepareCarry(at = carryDeadline)
        fill(c, 99)
        success(start(c))
        assertEquals(100, accountingRows(c.f, "background_jobs"))
        assertEquals(0, accountingRows(c.f, "leave_carry_expiries"))
    }

    @Test
    fun maximumCohortUsesOneExpiryOriginAndTwoJobs() {
        val c = prepareCarry()
        database()
            .update(
                """with inserted as (
            insert into persons(id,owner_company_id,legal_name,nationality)
            select gen_random_uuid(),?,'Expiry fixture employee','ID' from generate_series(1,4998) returning id)
            insert into employments(company_id,id,person_id,employee_number)
            select ?,gen_random_uuid(),id,replace(id::text,'-','') from inserted""",
                c.f.company,
                c.f.company,
            )
        val started = System.nanoTime()
        success(start(c))
        val elapsed = java.time.Duration.ofNanos(System.nanoTime() - started)
        assertEquals(5000, accountingRows(c.f, "leave_batch_targets"))
        assertEquals(2, accountingRows(c.f, "background_jobs"))
        assertEquals(1, accountingRows(c.f, "leave_carry_expiries"))
        val expiry =
            success(
                    transactions.run(accountingActor(c.f)) {
                        carryExpiries.list(c.f.company, null, 10)
                    }
                )
                .items
                .single()
        assertEquals(5000, expiry.totalEmployees)
        assertEquals(0, accountingRows(c.f, "leave_carry_expiry_outcomes"))
        println("Carry closing/expiry admission: ${elapsed.toMillis()} ms for 5000 employees")
    }
}
