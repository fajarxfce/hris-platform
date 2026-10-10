package dev.fajar.hris

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApprovalAdministrationHttpTest : ApprovalApiFixture() {
    @Test
    fun templateReadsKeepHistoricalRulesDistinctFromTheCurrentHeaderAndLatestConfiguration() {
        val f = leaveFixture()
        val id = UUID.randomUUID()
        val path = "/api/v1/companies/${f.company}/approvals/templates/$id"
        assertEquals(
            200,
            saveTemplate(f, id, templateBody(changes = mapOf("category" to "ORIGINAL")))
                .statusCode(),
        )
        assertEquals(
            200,
            saveTemplate(
                    f,
                    id,
                    templateBody(
                        version = 0,
                        changes =
                            mapOf(
                                "name" to "Updated policy",
                                "effectiveFrom" to "2027-01-01",
                                "category" to "UPDATED",
                                "minimumAmount" to "1234567890123456.78",
                            ),
                    ),
                )
                .statusCode(),
        )
        val latest = get(f.admin, path)
        assertEquals(200, latest.statusCode(), latest.body())
        val body = json.readTree(latest.body())
        assertEquals(1, body.get("version").asInt())
        assertEquals(1, body.get("appliedRevision").asInt())
        assertEquals("1234567890123456.78", body.get("minimumAmount").asString())
        val historical = get(f.admin, "$path?revision=0")
        assertEquals(200, historical.statusCode(), historical.body())
        val previous = json.readTree(historical.body())
        assertEquals(1, previous.get("version").asInt())
        assertEquals(0, previous.get("appliedRevision").asInt())
        assertEquals("ORIGINAL", previous.get("category").asString())
        assertEquals("2025-01-01", previous.get("effectiveFrom").asString())
        assertEquals("Updated policy", previous.get("name").asString())
        assertCode(get(f.admin, "$path?revision=2"), 404, "approval_template_not_found")
        assertCode(get(f.admin, "$path?revision=-1"), 422, "invalid_revision")
        val unrelated = reviewer(f.company)
        assertCode(get(unrelated.client, path), 403, "access_denied")
        val other = leaveFixture()
        assertCode(
            get(f.admin, "/api/v1/companies/${other.company}/approvals/templates/$id"),
            404,
            "approval_template_not_found",
        )
    }

    @Test
    fun delegationDetailsAreOwnedAndRemainReadableAfterDeactivationAndExpiry() {
        val f = leaveFixture()
        val to = reviewer(f.company)
        val unrelated = reviewer(f.company)
        val id = UUID.randomUUID()
        val path = "/api/v1/companies/${f.company}/approvals/delegations/$id"
        assertEquals(200, saveDelegation(f, id, delegationBody(f, to.account)).statusCode())
        for (client in listOf(f.supervisor, to.client, f.admin)) {
            val response = get(client, path)
            assertEquals(200, response.statusCode(), response.body())
            assertEquals(
                f.managerAccount.toString(),
                json.readTree(response.body()).get("fromAccount").asString(),
            )
            assertEquals(
                to.account.toString(),
                json.readTree(response.body()).get("toAccount").asString(),
            )
        }
        assertCode(get(unrelated.client, path), 404, "approval_delegation_not_found")
        assertEquals(
            200,
            saveDelegation(f, id, delegationBody(f, to.account, 0, false)).statusCode(),
        )
        clock.set(clock.instant().plusSeconds(3601))
        val expired = get(to.client, path)
        assertEquals(200, expired.statusCode(), expired.body())
        assertFalse(json.readTree(expired.body()).get("active").asBoolean())
        assertEquals(1, json.readTree(expired.body()).get("version").asInt())
        assertTrue(
            json
                .readTree(
                    get(to.client, "/api/v1/companies/${f.company}/approvals/delegations").body()
                )
                .get("items")
                .isEmpty
        )
        val other = leaveFixture()
        assertCode(
            get(f.admin, "/api/v1/companies/${other.company}/approvals/delegations/$id"),
            404,
            "approval_delegation_not_found",
        )
    }

    @Test
    fun assigneeLookupIsBoundedAndOnlyReturnsActiveScopedReferencesForTheRequestedKind() {
        val f = leaveFixture()
        val first = reviewer(f.company)
        val second = reviewer(f.company)
        val inactive = reviewer(f.company)
        val wrongPermission = reviewer(f.company, listOf("approvals.read", "expenses.approve"))
        for (account in
            listOf(
                first.account,
                second.account,
                inactive.account,
                wrongPermission.account,
            )) database()
            .update("update accounts set display_name='Scope_100% Approver' where id=?", account)
        database()
            .update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                f.company,
                inactive.account,
            )
        val other = leaveFixture()
        val foreign = reviewer(other.company)
        database()
            .update(
                "update accounts set display_name='Scope_100% Approver' where id=?",
                foreign.account,
            )
        val query = URLEncoder.encode("scope_100%", StandardCharsets.UTF_8)
        val path =
            "/api/v1/companies/${f.company}/approvals/assignees?kind=LEAVE&query=$query&limit=1"
        val initial = get(first.client, path)
        assertEquals(200, initial.statusCode(), initial.body())
        val one = json.readTree(initial.body())
        assertEquals(1, one.get("items").size())
        val cursor = one.get("nextCursor").asString()
        assertEquals(one.get("items")[0].get("id").asString(), cursor)
        assertEquals(setOf("id", "displayName"), one.get("items")[0].propertyNames().toSet())
        val next = get(first.client, "$path&after=$cursor")
        assertEquals(200, next.statusCode(), next.body())
        val two = json.readTree(next.body())
        assertEquals(1, two.get("items").size())
        assertTrue(two.get("nextCursor").isNull)
        assertEquals(
            setOf(first.account.toString(), second.account.toString()),
            setOf(
                one.get("items")[0].get("id").asString(),
                two.get("items")[0].get("id").asString(),
            ),
        )
        assertCode(get(wrongPermission.client, path), 403, "access_denied")
        assertCode(
            get(first.client, path.replace("kind=LEAVE", "kind=PAYROLL")),
            403,
            "access_denied",
        )
        assertCode(get(f.admin, path.replace("limit=1", "limit=201")), 422, "invalid_page")
        assertCode(get(f.admin, path.replace(query, "x".repeat(121))), 422, "invalid_page")
        val empty =
            get(
                first.client,
                path.replace(query, URLEncoder.encode("scope_100%missing", StandardCharsets.UTF_8)),
            )
        assertEquals(200, empty.statusCode(), empty.body())
        assertTrue(json.readTree(empty.body()).get("items").isEmpty)
    }
}
