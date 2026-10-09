package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
abstract class WorkforceAccessApiFixture : WorkPeriodApiFixture() {
    @Autowired protected lateinit var accountProbe: AccountLockProbe

    protected fun isolatedFixture(): Fixture {
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Workforce access fixture',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) select ?,permission from platform_permissions where account_id=(select id from accounts where email='admin@example.test')",
                account,
            )
        return fixture("$account@example.test")
    }

    protected fun waiting(
        account: UUID,
        change: () -> Unit,
        request: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { request() }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    protected fun shiftBody(id: UUID) =
        json.writeValueAsString(
            mapOf(
                "code" to "S${id.toString().take(8)}",
                "name" to "Office",
                "startsAt" to "08:00",
                "endsAt" to "17:00",
                "breakMinutes" to 60,
                "timezone" to "Asia/Jakarta",
                "mode" to "REMOTE",
                "reason" to "Schedule setup",
            )
        )

    protected fun createShift(f: Fixture): UUID {
        val id = UUID.randomUUID()
        val response =
            command(
                f.admin,
                "${f.path}/shifts/$id",
                shiftBody(id),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    protected fun anotherEmployee(f: Fixture): UUID {
        val id = UUID.randomUUID()
        val response =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "employeeNumber" to "E${id.toString().take(8)}",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "legalName" to "Another employee",
                                "nationality" to "ID",
                            ),
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2026-01-01",
                                "startDate" to "2026-01-01",
                                "contract" to "PERMANENT",
                                "status" to "ACTIVE",
                            ),
                        "reason" to "Onboarding",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }
}
