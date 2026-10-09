package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@Import(PayrollProbeConfiguration::class, AccountLockProbeConfiguration::class)
abstract class PayrollApiFixture : PeopleApiFixture() {
    @Autowired protected lateinit var payrollProbe: PayrollProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var runtimeJdbc: JdbcTemplate
    @Autowired protected lateinit var saveCompensation: SaveEmployeeCompensation

    protected data class PayrollMember(
        val account: UUID,
        val client: HttpClient,
        val csrf: String,
        val permissions: Set<String>,
    )

    protected data class PayrollFixture(
        val company: UUID,
        val employee: UUID,
        val admin: HttpClient,
        val adminCsrf: String,
        val operator: PayrollMember,
        val owner: PayrollMember,
    )

    @AfterEach
    fun clearPayrollHooks() {
        payrollProbe.clear()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    protected fun payrollMember(company: UUID, permissions: Set<String>): PayrollMember {
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Payroll fixture',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        val browser = client()
        return PayrollMember(account, browser, login(browser, "$account@example.test"), permissions)
    }

    protected fun payrollFixture(): PayrollFixture {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val owner = payrollMember(company, setOf("company.read", "payroll.self.read"))
        val operator =
            payrollMember(
                company,
                setOf(
                    "company.read",
                    "payroll.read",
                    "payroll.policy.manage",
                    "payroll.compensation.manage",
                ),
            )
        val employee = employee(admin, csrf, company, account = owner.account)
        return PayrollFixture(company, employee, admin, csrf, operator, owner)
    }

    protected fun policyBody(
        version: Long? = null,
        changes: Map<String, Any?> = emptyMap(),
    ): String =
        json.writeValueAsString(
            mapOf(
                "effectiveFrom" to "2026-01",
                "effectiveUntil" to "2026-12",
                "incomeTaxRuleId" to INDONESIAN_INCOME_TAX_2024,
                "insuranceRuleId" to INDONESIAN_INSURANCE_PU_V1,
                "minimumMonthlyWage" to "4000000",
                "healthWageCap" to "12000000",
                "pensionWageCap" to "10000000",
                "contributionRounding" to "HALF_UP",
                "reviewReferences" to listOf("https://example.test/fictional-2026-fixture"),
                "expectedVersion" to version,
                "reason" to "Reviewed payroll policy",
            ) + changes
        )

    protected fun compensationTerms(changes: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf(
            "basicSalary" to "10000000",
            "fixedEarnings" to
                listOf(
                    mapOf(
                        "code" to "ALLOWANCE",
                        "name" to "Fixed allowance",
                        "amount" to "500000",
                        "taxable" to true,
                    )
                ),
            "treatment" to "GROSS",
            "tax" to
                mapOf(
                    "residency" to "RESIDENT",
                    "ptkp" to "K0",
                    "residenceCountry" to "ID",
                    "verifiedOn" to "2026-09-30",
                    "verificationReference" to "Fixture verification",
                ),
            "insuranceWage" to "10500000",
            "insurancePrograms" to listOf("HEALTH", "OLD_AGE", "PENSION", "ACCIDENT", "DEATH"),
            "accidentRisk" to "VERY_LOW",
        ) + changes

    protected fun compensationBody(
        version: Long? = null,
        changes: Map<String, Any?> = emptyMap(),
        termChanges: Map<String, Any?> = emptyMap(),
    ): String =
        json.writeValueAsString(
            mapOf(
                "effectiveFrom" to "2026-01",
                "terms" to compensationTerms(termChanges),
                "expectedVersion" to version,
                "expectedEmploymentVersion" to 0,
                "reason" to "Reviewed compensation",
            ) + changes
        )

    protected fun policy(
        f: PayrollFixture,
        body: String = policyBody(),
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.operator,
    ) =
        command(
            member.client,
            "/api/v1/companies/${f.company}/payroll/policy",
            body,
            member.csrf,
            key,
            "PUT",
        )

    protected fun compensation(
        f: PayrollFixture,
        body: String = compensationBody(),
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.operator,
        employee: UUID = f.employee,
    ) =
        command(
            member.client,
            "/api/v1/companies/${f.company}/payroll/employees/$employee/compensation",
            body,
            member.csrf,
            key,
            "PUT",
        )

    protected fun payrollBody(result: HttpResponse<String>): tools.jackson.databind.JsonNode {
        assertEquals(200, result.statusCode(), result.body())
        return json.readTree(result.body())
    }

    protected fun payrollError(result: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, result.statusCode(), result.body())
        assertEquals(code, json.readTree(result.body()).get("code").asString())
    }

    protected fun payrollActor(f: PayrollFixture, member: PayrollMember = f.operator) =
        Actor(
            member.account,
            f.company,
            member.permissions,
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
        )

    protected fun count(company: UUID, table: String) =
        database()
            .queryForObject(
                "select count(*) from $table where company_id=?",
                Int::class.java,
                company,
            )!!
}
