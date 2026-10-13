package dev.fajar.hris

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseReadScopeHttpTest : ExpenseReviewApiFixture() {
    @Test
    fun selfScopeCannotExpandWithCompanyWideGrantsOrAnotherEmploymentSelection() {
        val f = expenseFixture()
        assertEquals(200, saveExpense(f).statusCode())
        val other = f.copy(claim = UUID.randomUUID(), employee = f.manager)
        assertEquals(200, saveExpense(other, asAdmin = true).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.read')",
                f.company,
                f.account,
            )
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        val range = "from=$today&until=$today"
        val all = get(f.worker, "${f.path}?$range")
        assertEquals(200, all.statusCode(), all.body())
        assertEquals(2, json.readTree(all.body())["items"].size())
        val own = get(f.worker, "${f.path}?$range&scope=SELF&employmentId=${f.employee}")
        assertEquals(200, own.statusCode(), own.body())
        assertEquals(
            listOf(f.claim.toString()),
            json
                .readTree(own.body())["items"]
                .iterator()
                .asSequence()
                .map { it["id"].asString() }
                .toList(),
        )
        assertEquals(403, get(f.worker, "${f.path}?$range&scope=SELF").statusCode())
        assertEquals(
            404,
            get(f.worker, "${f.path}?$range&scope=SELF&employmentId=${f.manager}").statusCode(),
        )
        for (suffix in listOf("", "/drafts", "/history", "/submissions", "/payments")) {
            val path = "${f.path}/${other.claim}$suffix"
            assertEquals(200, get(f.worker, path).statusCode(), suffix)
            val denied = get(f.worker, "$path?scope=SELF")
            assertEquals(404, denied.statusCode(), denied.body())
            assertFalse(denied.body().contains("Travel reimbursement"))
            assertEquals(
                200,
                get(f.worker, "${f.path}/${f.claim}$suffix?scope=SELF").statusCode(),
                suffix,
            )
            assertEquals(
                404,
                get(f.supervisor, "${f.path}/${f.claim}$suffix?scope=SELF").statusCode(),
                suffix,
            )
        }
    }

    @Test
    fun assignedReviewersKeepAuthorizedSubmissionAccessButCannotBroadenSelfScope() {
        val f = expenseFixture()
        expenseTemplate(f)
        val revision = readyReceipt(f)
        val submission = pendingExpense(f, readyLines(f, revision))
        val root = "/api/v1/companies/${f.company}/expenses/submissions/$submission"
        assertEquals(200, get(f.worker, "$root?scope=SELF").statusCode())
        assertEquals(200, get(f.supervisor, root).statusCode())
        assertEquals(404, get(f.supervisor, "$root?scope=SELF").statusCode())
        assertEquals(404, get(f.admin, "$root?scope=SELF").statusCode())
        val receipt = "$root/receipts/$revision/content"
        storageProbe.reads.set(0)
        assertEquals(404, get(f.supervisor, "$receipt?scope=SELF").statusCode())
        assertEquals(404, get(f.admin, "$receipt?scope=SELF").statusCode())
        assertEquals(0, storageProbe.reads.get())
        assertEquals(200, get(f.worker, "$receipt?scope=SELF").statusCode())
        assertEquals(200, get(f.supervisor, receipt).statusCode())
        val foreign = company(f.admin, f.adminCsrf)
        assertEquals(
            404,
            get(f.admin, root.replace(f.company.toString(), foreign.toString())).statusCode(),
        )
    }
}
