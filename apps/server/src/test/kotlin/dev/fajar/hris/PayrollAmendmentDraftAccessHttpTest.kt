package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentDraftAccessHttpTest : PayrollAmendmentDraftApiFixture() {
    @Test
    fun amendmentReadsAndCommandsRequireCurrentExplicitAuthorityAndExcludeTheBeneficiary() {
        val draft = draftFixture()
        val f = draft.source
        payrollBody(draftCommand(draft, "calculate", 0))
        val calculation = draftView(draft)["latestCalculation"]["id"].asString()
        val path = draftsPath(f) + "/${draft.id}"
        val reads =
            listOf(
                draftsPath(f) + "?year=2026",
                path,
                "$path/history",
                "$path/revisions/0",
                "$path/calculations/$calculation",
            )
        for (member in listOf(f.payroll.owner, f.payroll.operator, f.publication.finalizer)) {
            for (read in reads) payrollError(get(member.client, read), 403, "access_denied")
            payrollError(saveDraft(f, draft.id, draft.body, member = member), 403, "access_denied")
            payrollError(draftCommand(draft, "calculate", 1, member = member), 403, "access_denied")
            payrollError(draftCommand(draft, "cancel", 1, member = member), 403, "access_denied")
        }
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.amend')",
                f.payroll.company,
                f.payroll.owner.account,
            )
        for (read in reads.drop(1)) payrollError(
            get(f.payroll.owner.client, read),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
        assertEquals(0, payrollBody(get(f.payroll.owner.client, reads.first()))["items"].size())
        payrollError(
            saveDraft(f, UUID.randomUUID(), draft.body, member = f.payroll.owner),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
        payrollError(
            draftCommand(draft, "calculate", 1, member = f.payroll.owner),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
        payrollError(
            draftCommand(draft, "cancel", 1, member = f.payroll.owner),
            403,
            "payroll_amendment_self_preparation_forbidden",
        )
        val before = draftCounts(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.amend'",
                f.payroll.company,
                f.maker.account,
            )
        payrollError(saveDraft(f, draft.id, draft.body, draft.operation), 403, "access_denied")
        assertEquals(before, draftCounts(f))
    }

    @Test
    fun scopedQueriesAndRlsHideEveryDraftRevisionAndCalculationFromOtherCompanies() {
        val draft = draftFixture()
        val f = draft.source
        payrollBody(draftCommand(draft, "calculate", 0))
        val calculation = draftView(draft)["latestCalculation"]["id"].asString()
        val other = payrollFixture()
        val maker = payrollMember(other.company, setOf("company.read", "payroll.amend"))
        val path = "/api/v1/companies/${other.company}/payroll/amendments/${draft.id}"
        payrollError(
            get(maker.client, draftsPath(f) + "/${draft.id}"),
            403,
            "company_access_denied",
        )
        for (read in
            listOf(
                path,
                "$path/history",
                "$path/revisions/0",
                "$path/calculations/$calculation",
            )) payrollError(get(maker.client, read), 404, "payroll_amendment_not_found")
        val hidden =
            transactions.run(payrollActor(other, maker)) {
                Result.Success(
                    listOf(
                            "payroll_amendments",
                            "payroll_amendment_revisions",
                            "payroll_amendment_calculations",
                            "payroll_amendment_results",
                        )
                        .map { table ->
                            runtimeJdbc.queryForObject(
                                "select count(*) from $table where company_id=?",
                                Int::class.java,
                                f.payroll.company,
                            )
                        }
                )
            }
        assertEquals(Result.Success(listOf(0, 0, 0, 0)), hidden)
        val second = draftFixture(f)
        payrollError(
            get(f.maker.client, draftsPath(f) + "/${second.id}/calculations/$calculation"),
            404,
            "payroll_amendment_calculation_not_found",
        )
        payrollError(
            get(f.maker.client, draftsPath(f) + "/${UUID.randomUUID()}"),
            404,
            "payroll_amendment_not_found",
        )
    }
}
