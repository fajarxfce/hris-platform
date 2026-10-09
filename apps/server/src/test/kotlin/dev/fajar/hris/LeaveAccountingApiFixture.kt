package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.leave.domain.usecases.PostEmployeeLeaveAccrual
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@Import(LeaveAccountingProbeConfiguration::class, AccountLockProbeConfiguration::class)
@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
abstract class LeaveAccountingApiFixture : LeaveApiFixture() {
    @Autowired protected lateinit var accountingProbe: LeaveAccountingProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var runtimeJdbc: JdbcTemplate
    @Autowired protected lateinit var postAccrual: PostEmployeeLeaveAccrual

    @AfterEach
    fun clearAccountingProbes() {
        accountingProbe.clear()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    protected fun accountingFixture(
        at: Instant = Instant.parse("2026-10-01T15:00:00Z"),
        frequency: String = "MONTHLY",
        days: String = "1",
        carry: String = "2",
    ): LeaveFixture {
        val f = leaveFixture(at)
        accountingBody(accountingPolicy(f, frequency = frequency, days = days, carry = carry))
        return f
    }

    protected fun accountingPolicy(
        f: LeaveFixture,
        version: Long = 0,
        frequency: String = "MONTHLY",
        days: String = "1",
        carry: String = "2",
        from: String = "2026-01-01",
        minService: Int = 0,
        partial: Boolean = true,
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/leave/types/${f.type}",
            json.writeValueAsString(
                mapOf(
                    "code" to "ANNUAL",
                    "name" to "Annual leave",
                    "effectiveFrom" to from,
                    "paid" to true,
                    "allowPartialDays" to partial,
                    "minServiceMonths" to minService,
                    "expectedVersion" to version,
                    "accrual" to
                        mapOf(
                            "frequency" to frequency,
                            "daysPerPeriod" to days,
                            "carryLimitDays" to carry,
                        ),
                    "reason" to "Entitlement policy",
                )
            ),
            f.adminCsrf,
            key,
            "PUT",
        )

    protected fun accrualBody(
        id: UUID = UUID.randomUUID(),
        month: String = "2026-09",
        balanceVersion: Long = 0,
        policyVersion: Long = 1,
        employmentVersion: Long = 0,
    ): String =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "month" to month,
                "expectedEmploymentVersion" to employmentVersion,
                "expectedPolicyVersion" to policyVersion,
                "expectedBalanceVersion" to balanceVersion,
                "reason" to "Monthly entitlement",
            )
        )

    protected fun accrue(
        f: LeaveFixture,
        body: String = accrualBody(),
        key: UUID = UUID.randomUUID(),
        client: HttpClient = f.admin,
        csrf: String = f.adminCsrf,
    ): HttpResponse<String> =
        command(
            client,
            "/api/v1/companies/${f.company}/leave/employees/${f.employee}/accruals/${f.type}",
            body,
            csrf,
            key,
        )

    protected fun closingBody(
        id: UUID = UUID.randomUUID(),
        sourceVersion: Long = 1,
        policyVersion: Long = 1,
        destinationVersion: Long? = 0,
    ): String =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "expectedPolicyVersion" to policyVersion,
                "expectedBalanceVersion" to sourceVersion,
                "expectedDestinationVersion" to destinationVersion,
                "reason" to "Annual balance closing",
            )
        )

    protected fun closeYear(
        f: LeaveFixture,
        body: String = closingBody(),
        key: UUID = UUID.randomUUID(),
        year: Int = 2026,
        client: HttpClient = f.admin,
        csrf: String = f.adminCsrf,
    ): HttpResponse<String> =
        command(
            client,
            "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/$year/closing",
            body,
            csrf,
            key,
        )

    protected fun entitlementView(
        f: LeaveFixture,
        year: Int = 2026,
        client: HttpClient = f.worker,
    ): JsonNode =
        accountingBody(
            get(
                client,
                "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/$year/entitlements",
            )
        )

    protected fun accountingBody(result: HttpResponse<String>): JsonNode {
        assertEquals(200, result.statusCode(), result.body())
        return json.readTree(result.body())
    }

    protected fun accountingCode(
        result: HttpResponse<String>,
        status: Int,
        code: String,
    ): JsonNode {
        assertEquals(status, result.statusCode(), result.body())
        val body = json.readTree(result.body())
        assertEquals(code, body["code"].asString(), result.body())
        return body
    }

    protected fun accountingRows(f: LeaveFixture, table: String): Int =
        database()
            .queryForObject(
                "select count(*) from $table where company_id=?",
                Int::class.java,
                f.company,
            )!!

    protected fun accountingActor(f: LeaveFixture): Actor =
        Actor(
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!,
            f.company,
            PermissionCatalog.companyAdministrator,
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
        )
}
