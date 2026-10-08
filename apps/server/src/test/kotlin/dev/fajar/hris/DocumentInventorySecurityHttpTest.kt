package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentInventorySecurityHttpTest : DocumentInventoryApiFixture() {
    @Test
    fun permissionCancellationCredentialAndLeaseChangesRejectPendingObservations() {
        for (case in listOf("permission", "cancel", "lease", "credential")) {
            val f = fixture()
            val key = orphan(f)
            val lease = beginInventory(f)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            storageProbe.beforeList = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<JobStep>> { runInventory(f, lease) }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    when (case) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='documents.inventory'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        "credential" ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                        "cancel" ->
                            assertTrue(
                                cancelJob.execute(f.actor, lease.job.request.id, lease.job.version)
                                    is Result.Success
                            )
                        else ->
                            database()
                                .update(
                                    "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                                    lease.job.request.id,
                                )
                    }
                } finally {
                    release.countDown()
                }
                val result = pending.get(10, TimeUnit.SECONDS)
                assertTrue(result is Result.Failed, "$case: $result")
                val expected =
                    when (case) {
                        "permission" -> "access_denied"
                        "credential" -> "session_revoked"
                        "cancel" -> "job_cancellation_requested"
                        else -> "job_lease_lost"
                    }
                assertEquals(expected, (result as Result.Failed).failure.code)
            }
            storageProbe.beforeList = null
            assertEquals(0, inventoryPages(inventoryId(lease)))
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where object_key=?",
                        Int::class.java,
                        key,
                    ),
            )
            assertTrue(storageProbe.objects.containsKey(key))
            if (case == "lease") {
                val replacement =
                    claimDocuments(JobKind.DOCUMENT_INVENTORY).single {
                        it.job.request.id == lease.job.request.id
                    }
                assertEquals(Result.Success(JobStep(1, true)), runInventory(f, replacement))
            }
            closeInventoryJobs()
        }
    }

    @Test
    fun competingStartsReplayAndCrossCompanyReadsRespectCurrentAccess() {
        val f = fixture()
        val gate = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val requests =
                (1..2).map {
                    pool.submit<Int> {
                        check(gate.await(5, TimeUnit.SECONDS))
                        startInventory(f).statusCode()
                    }
                }
            gate.countDown()
            assertEquals(listOf(200, 409), requests.map { it.get(10, TimeUnit.SECONDS) }.sorted())
        }
        val lease = claimDocuments(JobKind.DOCUMENT_INVENTORY).single()
        val id = inventoryId(lease)
        val other = fixture()
        assertEquals(404, get(other.browser, "${other.path}/inventory/$id").statusCode())
        assertEquals(404, get(other.browser, "${other.path}/inventory/$id/pages").statusCode())
        assertEquals(Result.Success(JobStep(1, true)), runInventory(f, lease))
        val operation = UUID.randomUUID()
        val next = UUID.randomUUID()
        val original = startInventory(f, next, operation)
        assertEquals(200, original.statusCode(), original.body())
        assertEquals(original.body(), startInventory(f, next, operation).body())
        assertEquals(409, startInventory(f, UUID.randomUUID(), operation).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='documents.inventory'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, startInventory(f, next, operation).statusCode())
        assertEquals(403, get(f.browser, "${f.path}/inventory/$id").statusCode())
    }

    @Test
    fun inventoryReadersCannotResumeOrAcquireBroaderDocumentPermissions() {
        val f = fixture()
        val lease = beginInventory(f)
        val id = inventoryId(lease)
        assertEquals(
            Result.Success(Unit),
            inventoryAbort.execute(lease, Failure(FailureKind.UNAVAILABLE, "storage_unavailable")),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission in ('jobs.retry','documents.read','people.profile.read','documents.manage','people.profile.manage')",
                f.company,
                f.actor.accountId,
            )
        assertEquals(0, inventory(f, id).get("availableActions").size())
        assertEquals(403, resumeInventory(f, id, 0).statusCode())
        assertEquals(200, get(f.browser, "${f.path}/inventory/$id/pages").statusCode())
        assertEquals(200, get(f.browser, "${f.path}/inventory/$id/attempts").statusCode())
    }

    @Test
    fun boundedRequestsAndRecentAuthenticationApplyBeforeQueueing() {
        val f = fixture()
        val id = UUID.randomUUID()
        assertEquals(422, startInventory(f, id, reason = "").statusCode())
        assertEquals(422, startInventory(f, id, reason = "x".repeat(1001)).statusCode())
        assertEquals(422, get(f.browser, "${f.path}/inventory?limit=101").statusCode())
        val lease = beginInventory(f, id)
        assertEquals(422, get(f.browser, "${f.path}/inventory/$id/pages?after=10001").statusCode())
        assertEquals(422, get(f.browser, "${f.path}/inventory/$id/pages?limit=0").statusCode())
        assertEquals(
            Result.Success(Unit),
            inventoryAbort.execute(lease, Failure(FailureKind.UNAVAILABLE, "storage_unavailable")),
        )
        clock.set(clock.instant().plusSeconds(601))
        val denied = resumeInventory(f, id, 0)
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals(
            "recent_authentication_required",
            json.readTree(denied.body()).get("code").asString(),
        )
        assertEquals(1, inventory(f, id).get("attempts").asInt())
    }

    @Test
    fun cancellationWhileListingIsInterruptedAndCanRecoverWithANewLease() {
        val f = fixture()
        orphan(f)
        val lease = beginInventory(f)
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        storageProbe.beforeList = {
            entered.countDown()
            try {
                check(CountDownLatch(1).await(10, TimeUnit.SECONDS))
            } finally {
                exited.countDown()
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<JobStep>> { runInventory(f, lease) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
            } finally {
                pending.cancel(true)
            }
            assertTrue(exited.await(5, TimeUnit.SECONDS))
        }
        storageProbe.beforeList = null
        assertEquals(0, inventoryPages(inventoryId(lease)))
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        val next = claimDocuments(JobKind.DOCUMENT_INVENTORY).single()
        assertEquals(Result.Success(JobStep(1, true)), runInventory(f, next))
    }
}
