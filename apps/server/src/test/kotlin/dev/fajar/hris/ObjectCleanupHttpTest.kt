package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.domain.entities.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class ObjectCleanupHttpTest : ObjectCleanupApiFixture() {
    @Test
    fun retainedObjectsCannotBeClaimedAndQueueReadsStayWithinCompanyScope() {
        val f = fixture()
        val r = schedule(f, false)
        val other = fixture()
        assertEquals(
            Result.Success(128L),
            transactions.run(f.actor) { queue.allocatedBytes(f.company) },
        )
        assertEquals(
            Result.Success<Any?>(null),
            transactions.run(other.actor) { queue.find(f.company, r.id) },
        )
        val list = get(f.browser, f.path)
        assertEquals(200, list.statusCode(), list.body())
        assertFalse(list.body().contains(r.key))
        assertEquals(0, json.readTree(get(other.browser, other.path).body()).get("items").size())
        assertEquals(422, get(f.browser, "${f.path}?limit=201").statusCode())
        assertTrue(
            transactions.run(f.actor) {
                queue.retain(f.company, setOf(r.id)).flatMap {
                    Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
                }
            } is Result.Failed
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where id=?",
                    Int::class.java,
                    r.id,
                ),
        )
        assertEquals(
            Result.Success(1),
            transactions.run(f.actor) { queue.retain(f.company, setOf(r.id)) },
        )
        val storage = StorageProbe()
        assertEquals(Result.Success(0), worker(storage).collect.execute(UUID.randomUUID()))
        assertTrue(storage.deleted.isEmpty())
    }

    @Test
    fun claimsAreWorkerOnlyFencedAndCannotRaceWithRetention() {
        val f = fixture()
        val r = schedule(f)
        val w = worker()
        val denied = queue.claim(UUID.randomUUID(), 1, 120, 8)
        assertEquals(FailureKind.FORBIDDEN, (denied as Result.Failed).failure.kind)
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        val leases =
            Executors.newFixedThreadPool(2).use { pool ->
                val tasks =
                    (1..2).map {
                        pool.submit<List<ObjectCleanupLease>> {
                            ready.countDown()
                            check(go.await(5, TimeUnit.SECONDS))
                            (w.queue.claim(UUID.randomUUID(), 1, 120, 8) as Result.Success).value
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                go.countDown()
                tasks.flatMap { it.get(10, TimeUnit.SECONDS) }
            }
        assertEquals(1, leases.size)
        val first = leases.single()
        assertEquals(
            Result.Success(0),
            transactions.run(f.actor) { queue.retain(f.company, setOf(r.id)) },
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update object_cleanup_queue set object_key=?,version=version+1 where id=?",
                    "${f.company}/other",
                    r.id,
                )
        }
        expire(r.id)
        val second = (w.queue.claim(UUID.randomUUID(), 1, 120, 8) as Result.Success).value.single()
        assertNotEquals(first.token, second.token)
        assertEquals(Result.Success(false), w.transactions.run(f.actor) { w.queue.complete(first) })
        assertEquals(Result.Success(true), w.transactions.run(f.actor) { w.queue.complete(second) })
        val person = UUID.randomUUID()
        database()
            .update(
                "insert into persons(id,owner_company_id,legal_name,nationality) values(?,?,'Private employee','ID')",
                person,
                f.company,
            )
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,'ISOLATED')",
                f.company,
                UUID.randomUUID(),
                person,
            )
        assertEquals(0, w.jdbc.queryForObject("select count(*) from employments", Int::class.java))
    }

    @Test
    fun failedAuditKeepsDeletionRecoverableAndRepeatedPhysicalDeletionIsHarmless() {
        val f = fixture()
        val r = schedule(f)
        val storage = StorageProbe()
        val w = worker(storage)
        database()
            .execute(
                """create function fail_cleanup_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='${r.id}'::uuid and new.action='storage.object_deleted' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger cleanup_audit_probe before insert on audit_entries for each row execute function fail_cleanup_audit()"
            )
        try {
            assertTrue(w.collect.execute(UUID.randomUUID(), 1) is Result.Failed)
            assertEquals(1, storage.calls.get())
            assertEquals(
                "RUNNING",
                database()
                    .queryForObject(
                        "select status from object_cleanup_queue where id=?",
                        String::class.java,
                        r.id,
                    ),
            )
        } finally {
            database().execute("drop trigger cleanup_audit_probe on audit_entries")
            database().execute("drop function fail_cleanup_audit()")
        }
        expire(r.id)
        assertEquals(Result.Success(1), w.collect.execute(UUID.randomUUID(), 1))
        assertEquals(2, storage.calls.get())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where id=?",
                    Int::class.java,
                    r.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='storage.object_deleted'",
                    Int::class.java,
                    r.id,
                ),
        )
    }

    @Test
    fun providerFailuresBackOffAndStopUntilAnExplicitAuditedRetry() {
        val f = fixture()
        val r = schedule(f)
        val storage = StorageProbe().apply { fail = true }
        val w = worker(storage)
        assertEquals(Result.Success(0), w.collect.execute(UUID.randomUUID(), 1))
        assertEquals(1, storage.calls.get())
        assertEquals(Result.Success(0), w.collect.execute(UUID.randomUUID(), 1))
        assertEquals(1, storage.calls.get())
        for (attempt in 2..8) {
            due(r.id)
            assertEquals(Result.Success(0), w.collect.execute(UUID.randomUUID(), 1))
        }
        assertEquals(8, storage.calls.get())
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from object_cleanup_queue where id=?",
                    String::class.java,
                    r.id,
                ),
        )
        val version =
            database()
                .queryForObject(
                    "select version from object_cleanup_queue where id=?",
                    Long::class.java,
                    r.id,
                )!!
        val key = UUID.randomUUID()
        val response = retry(f, r.id, version, key)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(409, retry(f, r.id, version).statusCode())
        assertEquals(response.body(), retry(f, r.id, version, key).body())
        storage.fail = false
        assertEquals(Result.Success(1), w.collect.execute(UUID.randomUUID(), 1))
        assertEquals(9, storage.calls.get())
        assertEquals(response.body(), retry(f, r.id, version, key).body())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='storage.cleanup_failed'",
                    Int::class.java,
                    r.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='storage.cleanup_retry_requested'",
                    Int::class.java,
                    r.id,
                ),
        )
    }

    @Test
    fun repeatedCrashesExhaustLeasesWithoutDeletingRetainedBusinessData() {
        val f = fixture()
        val r = schedule(f)
        val probe = StorageProbe()
        val w = worker(probe)
        repeat(8) {
            assertEquals(
                1,
                (w.queue.claim(UUID.randomUUID(), 1, 120, 8) as Result.Success).value.size,
            )
            expire(r.id)
        }
        assertEquals(Result.Success(0), w.collect.execute(UUID.randomUUID(), 1))
        assertTrue(probe.deleted.isEmpty())
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from object_cleanup_queue where id=?",
                    String::class.java,
                    r.id,
                ),
        )
        assertEquals(
            "cleanup_attempts_exhausted",
            database()
                .queryForObject(
                    "select failure_code from object_cleanup_queue where id=?",
                    String::class.java,
                    r.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='storage.cleanup_exhausted'",
                    Int::class.java,
                    r.id,
                ),
        )
        val other = fixture()
        val version =
            database()
                .queryForObject(
                    "select version from object_cleanup_queue where id=?",
                    Long::class.java,
                    r.id,
                )!!
        assertEquals(404, retry(other, r.id, version).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='jobs.retry'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, retry(f, r.id, version).statusCode())
    }
}
