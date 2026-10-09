package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientPolicySyncHttpTest : MobileSyncApiFixture() {
    private fun policy(f: ExpenseFixture, disabled: List<String>, version: Long? = null) {
        val result =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/settings/client-policy",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to version,
                        "activateAt" to null,
                        "disabledModules" to disabled,
                        "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                        "maintenance" to null,
                        "reason" to "Company capability configuration",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, result.statusCode(), result.body())
    }

    @Test
    fun disabledCapabilitiesCannotBeReadThroughSyncAndReenablingPreservesValidCursors() {
        val f = expenseFixture()
        assertEquals(200, saveExpense(f).statusCode())
        val before = body(bootstrap(f))
        val cursor = before["changesCursor"].asString()
        policy(f, listOf("EXPENSES"))
        error(bootstrap(f), 403, "company_module_disabled")
        error(changes(f, cursor), 403, "company_module_disabled")
        error(get(f.worker, "${f.path}/${f.claim}"), 403, "company_module_disabled")
        assertEquals(
            200,
            get(f.worker, "/api/v1/companies/${f.company}/client-policy").statusCode(),
        )
        policy(f, emptyList(), 0)
        body(changes(f, cursor))
        assertEquals(200, get(f.worker, "${f.path}/${f.claim}").statusCode())
    }

    @Test
    fun explicitCollectionSelectionCanContinueAnEnabledCapabilityWithoutGrantingOthers() {
        val f = expenseFixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'announcements.read')",
                f.company,
                f.account,
            )
        policy(f, listOf("EXPENSES"))
        val root = "/api/v1/companies/${f.company}/sync"
        val inbox = body(get(f.worker, "$root/bootstrap?collections=INBOX"))
        assertEquals(
            listOf("INBOX"),
            inbox["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        error(
            get(f.worker, "$root/bootstrap?collections=INBOX,EXPENSE_CLAIMS"),
            403,
            "company_module_disabled",
        )
        error(get(f.worker, "$root/bootstrap?collections=UNKNOWN"), 400, "invalid_request")
        error(get(f.worker, "$root/bootstrap?collections="), 422, "invalid_sync_collections")
        error(get(f.worker, "$root/bootstrap?collections=PAYSLIPS"), 403, "sync_access_denied")
    }
}
