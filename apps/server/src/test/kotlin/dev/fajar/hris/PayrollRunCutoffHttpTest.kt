package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class PayrollRunCutoffHttpTest : PayrollRunApiFixture() {
    @Test
    fun relevantSourcesFreezeButFuturePolicyAndCompensationRetainTheCapturedRevisions() {
        val f = calculationFixture()
        val p = f.people
        val lease = beginRun(f)
        for (response in
            listOf(
                policy(p.payroll, policyBody(0)),
                compensation(p.payroll, compensationBody(0)),
                inputSave(p, f.work, inputBody(f.work, 1)),
                saveRunOpening(p, 1),
            )) payrollError(response, 409, "payroll_period_frozen")
        for (table in listOf("payroll_inputs", "payroll_tax_openings")) assertThrows(
            DataAccessException::class.java
        ) {
            database()
                .update("update $table set version=version+1 where company_id=?", p.payroll.company)
        }
        payrollBody(policy(p.payroll, policyBody(0, mapOf("effectiveFrom" to "2026-10"))))
        payrollBody(
            compensation(
                p.payroll,
                compensationBody(
                    0,
                    mapOf("effectiveFrom" to "2026-10"),
                    mapOf("basicSalary" to "20000000", "payBasis" to runPayBasis()),
                ),
            )
        )
        drainRun(f, lease)
        val detail =
            payrollBody(
                get(
                    p.reviewer.client,
                    runPath(f, runId(lease)) + "/employees/${p.payroll.employee}",
                )
            )
        assertEquals(0, detail["facts"]["policy"]["appliedRevision"].asLong())
        assertEquals(0, detail["facts"]["policy"]["version"].asLong())
        assertEquals("10000000", detail["facts"]["compensation"]["basicSalary"].asString())
        payrollError(inputSave(p, f.work, inputBody(f.work, 1)), 409, "payroll_period_frozen")
        payrollBody(abandonRun(f, runId(lease)))
        payrollBody(inputSave(p, f.work, inputBody(f.work, 1)))
        payrollBody(saveRunOpening(p, 1))
    }

    @Test
    fun changedEmploymentIsReportedForThatEmployeeWithoutMixingSourceRevisions() {
        val f = calculationFixture()
        val lease = beginRun(f)
        val p = f.people.payroll
        payrollBody(
            revise(p.admin, p.adminCsrf, p.company, p.employee, 0, terms(from = "2026-10-02"))
        )
        drainRun(f, lease)
        val row = runView(f, runId(lease))["results"]["items"][0]
        assertEquals("stale_employment_version", row["failure"]["code"].asString())
        assertTrue(row["takeHome"].isNull)
    }

    private data class LeaveSetup(val f: ProcessingFixture, val type: UUID)

    private fun leaveSetup(): LeaveSetup {
        val f = processingFixture()
        val p = f.payroll
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.self.manage')",
                p.company,
                p.owner.account,
            )
        val type = UUID.randomUUID()
        payrollBody(
            command(
                p.admin,
                "/api/v1/companies/${p.company}/leave/types/$type",
                json.writeValueAsString(
                    mapOf(
                        "code" to "ANNUAL",
                        "name" to "Annual leave",
                        "effectiveFrom" to "2026-01-01",
                        "paid" to false,
                        "allowPartialDays" to true,
                        "minServiceMonths" to 0,
                        "maxRequestDays" to 30,
                        "active" to true,
                        "reason" to "Reviewed leave policy",
                    )
                ),
                p.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(
            command(
                p.admin,
                "/api/v1/companies/${p.company}/leave/employees/${p.employee}/balances/$type/2026/adjustments",
                """{"days":"5","expectedVersion":0,"reason":"Owned leave entitlement"}""",
                p.adminCsrf,
                UUID.randomUUID(),
            )
        )
        val shift = UUID.randomUUID()
        payrollBody(
            command(
                p.admin,
                "/api/v1/companies/${p.company}/workforce/shifts/$shift",
                """{"code":"DAY","name":"Day shift","startsAt":"08:00","endsAt":"16:00","breakMinutes":60,"timezone":"Asia/Jakarta","mode":"REMOTE","reason":"Work schedule"}""",
                p.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(
            command(
                p.admin,
                "/api/v1/companies/${p.company}/workforce/employees/${p.employee}/schedule",
                json.writeValueAsString(
                    mapOf(
                        "effectiveFrom" to "2026-01-01",
                        "days" to
                            java.time.DayOfWeek.entries.associate {
                                it.name to mapOf("id" to shift, "version" to 0)
                            },
                        "reason" to "Reviewed work calendar",
                    )
                ),
                p.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        for (kind in listOf("LEAVE", "LEAVE_CANCELLATION")) payrollBody(
            command(
                p.admin,
                "/api/v1/companies/${p.company}/approvals/templates/${UUID.randomUUID()}",
                json.writeValueAsString(
                    mapOf(
                        "name" to "Independent leave approval",
                        "kind" to kind,
                        "effectiveFrom" to "2026-01-01",
                        "minimumAmount" to "0",
                        "stages" to
                            listOf(
                                mapOf(
                                    "assignment" to "NAMED",
                                    "accountIds" to listOf(f.actor.accountId),
                                )
                            ),
                        "reason" to "Reviewed approval assignment",
                    )
                ),
                p.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        return LeaveSetup(f, type)
    }

    private fun submitLeave(
        setup: LeaveSetup,
        id: UUID,
        date: String = "2026-09-30",
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            setup.f.payroll.owner.client,
            "/api/v1/companies/${setup.f.payroll.company}/leave/requests",
            json.writeValueAsString(
                mapOf(
                    "id" to id,
                    "employeeId" to setup.f.payroll.employee,
                    "typeId" to setup.type,
                    "days" to listOf(mapOf("workDate" to date, "portion" to "FULL")),
                    "reason" to "Personal leave",
                )
            ),
            setup.f.payroll.owner.csrf,
            key,
        )

    private fun decideLeave(
        setup: LeaveSetup,
        id: UUID,
        version: Long,
        decision: String = "APPROVE",
    ) =
        command(
            setup.f.payroll.admin,
            "/api/v1/companies/${setup.f.payroll.company}/leave/requests/$id/decisions",
            json.writeValueAsString(
                mapOf("version" to version, "decision" to decision, "reason" to "Reviewed absence")
            ),
            setup.f.payroll.adminCsrf,
            UUID.randomUUID(),
        )

    private fun cancelLeave(setup: LeaveSetup, id: UUID, version: Long) =
        command(
            setup.f.payroll.owner.client,
            "/api/v1/companies/${setup.f.payroll.company}/leave/requests/$id/cancellation",
            json.writeValueAsString(mapOf("version" to version, "reason" to "Changed leave plans")),
            setup.f.payroll.owner.csrf,
            UUID.randomUUID(),
        )

    private fun closeForPayroll(setup: LeaveSetup): CalculationFixture {
        payrollBody(policy(setup.f.payroll))
        val work = closeWork(setup.f)
        val period = UUID.fromString(payrollBody(periodCreate(setup.f))["id"].asString())
        return CalculationFixture(setup.f, work, period)
    }

    @Test
    fun pendingLeaveAndCancellationMustSettleBeforeCutoffAndCannotChangeAfterwards() {
        val setup = leaveSetup()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val submitted = payrollBody(submitLeave(setup, id, key = key))
        val f = closeForPayroll(setup)
        payrollError(createRun(f), 409, "payroll_leave_decision_pending")
        payrollBody(decideLeave(setup, id, 0))
        payrollBody(cancelLeave(setup, id, 1))
        payrollError(createRun(f), 409, "payroll_leave_decision_pending")
        payrollBody(decideLeave(setup, id, 2, "REJECT"))
        val lease = beginRun(f)
        assertEquals(submitted, payrollBody(submitLeave(setup, id, key = key)))
        val requestPath = "/api/v1/companies/${setup.f.payroll.company}/leave/requests/$id"
        assertFalse(
            payrollBody(get(setup.f.payroll.owner.client, requestPath))["availableActions"]
                .toString()
                .contains("REQUEST_CANCELLATION")
        )
        payrollError(cancelLeave(setup, id, 3), 409, "payroll_period_frozen")
        payrollError(
            submitLeave(setup, UUID.randomUUID(), "2026-09-29"),
            409,
            "payroll_period_frozen",
        )
        payrollBody(submitLeave(setup, UUID.randomUUID(), "2026-10-02"))
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update leave_requests set version=version+1 where company_id=? and id=?",
                    setup.f.payroll.company,
                    id,
                )
        }
        stopRun(f, lease)
        payrollBody(abandonRun(f, runId(lease), 1, 1))
        assertTrue(
            payrollBody(get(setup.f.payroll.owner.client, requestPath))["availableActions"]
                .toString()
                .contains("REQUEST_CANCELLATION")
        )
        payrollBody(cancelLeave(setup, id, 3))
    }

    @Test
    fun aLeaveSubmissionHoldingItsCutoffGuardCommitsBeforeACompetingPayrollStart() {
        val setup = leaveSetup()
        val f = closeForPayroll(setup)
        val barrier = AccountLockProbe.Barrier(setup.f.payroll.owner.account)
        accountProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { executor ->
            val leave =
                executor.submit<java.net.http.HttpResponse<String>> {
                    submitLeave(setup, UUID.randomUUID())
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val run = executor.submit<java.net.http.HttpResponse<String>> { createRun(f) }
                barrier.release.countDown()
                payrollBody(leave.get(10, TimeUnit.SECONDS))
                payrollError(run.get(10, TimeUnit.SECONDS), 409, "payroll_leave_decision_pending")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, count(f.people.payroll.company, "payroll_runs"))
        assertEquals(1, count(f.people.payroll.company, "leave_requests"))
    }
}
