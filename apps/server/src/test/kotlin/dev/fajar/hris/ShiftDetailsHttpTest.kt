package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.entities.ShiftDefinition
import dev.fajar.hris.workforce.domain.usecases.GetShift
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ShiftDetailsHttpTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var details: GetShift

    @Test
    fun detailsReturnTheCurrentVersionIncludingInactiveTemplatesWithoutCrossCompanyDisclosure() {
        val f = isolatedFixture()
        val other = isolatedFixture()
        val id = createShift(f)
        val foreign = createShift(other)
        val path = "${f.path}/shifts/$id"
        val initial = get(f.admin, path)
        assertEquals(200, initial.statusCode(), initial.body())
        val shift = json.readTree(initial.body())
        assertEquals(id.toString(), shift["id"].asString())
        assertEquals(0, shift["version"].asLong())
        assertTrue(shift["active"].asBoolean())
        assertEquals("Asia/Jakarta", shift["details"]["timezone"].asString())
        assertEquals(403, get(f.employeeClient, path).statusCode())
        for (missing in listOf(foreign, UUID.randomUUID())) {
            val response = get(f.admin, "${f.path}/shifts/$missing")
            assertEquals(404, response.statusCode(), response.body())
            assertEquals("shift_not_found", json.readTree(response.body())["code"].asString())
        }
        val updated =
            command(
                f.admin,
                path,
                json.writeValueAsString(
                    mapOf(
                        "code" to "S${id.toString().take(8)}",
                        "name" to "Office",
                        "startsAt" to "08:00",
                        "endsAt" to "17:00",
                        "breakMinutes" to 60,
                        "timezone" to "Asia/Jakarta",
                        "mode" to "REMOTE",
                        "active" to false,
                        "expectedVersion" to 0,
                        "reason" to "Deactivate template",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, updated.statusCode(), updated.body())
        val current = get(f.admin, path)
        assertEquals(200, current.statusCode(), current.body())
        assertEquals(1, json.readTree(current.body())["version"].asLong())
        assertFalse(json.readTree(current.body())["active"].asBoolean())
    }

    @Test
    fun removalOfAuthorityOrCredentialsDuringTheDetailGuardPreventsDisclosure() {
        for (credentials in listOf(false, true)) {
            val f = isolatedFixture()
            val id = createShift(f)
            val barrier = AccountLockProbe.Barrier(f.actor.accountId, occurrence = 2)
            accountProbe.current.set(barrier)
            val result =
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending =
                        pool.submit<HttpResponse<String>> { get(f.admin, "${f.path}/shifts/$id") }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        if (credentials)
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                        else
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.read'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        barrier.release.countDown()
                        pending.get(10, TimeUnit.SECONDS)
                    } finally {
                        barrier.release.countDown()
                        accountProbe.current.set(null)
                    }
                }
            assertEquals(if (credentials) 401 else 403, result.statusCode(), result.body())
            assertFalse(result.body().contains("Office"))
        }
    }

    @Test
    fun cancellingAPendingDetailReleasesItsSharedScheduleAndAccessGuards() {
        val f = isolatedFixture()
        val id = createShift(f)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.read')",
                f.company,
                f.employeeAccount,
            )
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        val finished = CountDownLatch(1)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<ShiftDefinition>> {
                    try {
                        details.execute(f.actor, id)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val second = get(f.employeeClient, "${f.path}/shifts/$id")
                assertEquals(200, second.statusCode(), second.body())
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            "calendar:${f.company}",
                        ),
                )
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertTrue(pending.isCancelled)
                assertEquals(
                    true,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            "calendar:${f.company}",
                        ),
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(200, get(f.admin, "${f.path}/shifts/$id").statusCode())
        createShift(f)
    }
}
