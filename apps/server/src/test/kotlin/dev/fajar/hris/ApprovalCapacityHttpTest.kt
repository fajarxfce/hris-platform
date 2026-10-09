package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApprovalCapacityHttpTest : ApprovalApiFixture() {
    @Test
    fun concurrentTemplatesShareTheActiveLimitAndAdministrationRemainsPaged() {
        val f = leaveFixture()
        seedTemplates(f, 199)
        val ids = List(2) { UUID.randomUUID() }
        val go = CountDownLatch(1)
        val results =
            Executors.newFixedThreadPool(2).use { pool ->
                val tasks =
                    ids.map { id ->
                        pool.submit<HttpResponse<String>> {
                            check(go.await(5, TimeUnit.SECONDS))
                            saveTemplate(f, id)
                        }
                    }
                go.countDown()
                tasks.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 409), results.map { it.statusCode() }.sorted())
        val winner = ids[results.indexOfFirst { it.statusCode() == 200 }]
        val loser = ids[results.indexOfFirst { it.statusCode() == 409 }]
        assertEquals(
            200,
            saveTemplate(f, UUID.randomUUID(), templateBody(active = false)).statusCode(),
        )
        assertEquals(
            200,
            saveTemplate(f, winner, templateBody(version = 0, active = false)).statusCode(),
        )
        assertEquals(200, saveTemplate(f, loser).statusCode())
        assertCode(
            saveTemplate(f, winner, templateBody(version = 1)),
            409,
            "approval_policy_capacity",
        )
        val path = "/api/v1/companies/${f.company}/approvals/templates?kind=EXPENSE&asOf=2026-10-01"
        val first = json.readTree(get(f.admin, "$path&limit=1").body())
        assertEquals(1, first.get("items").size())
        val next = first.get("nextCursor").asString()
        val second = json.readTree(get(f.admin, "$path&limit=1&after=$next").body())
        assertNotEquals(
            first.get("items")[0].get("id").asString(),
            second.get("items")[0].get("id").asString(),
        )
        assertEquals(422, get(f.admin, "$path&limit=201").statusCode())
        assertEquals(403, get(f.worker, path).statusCode())
    }

    @Test
    fun overCapacityPolicySelectionFailsClosedAndArchivalAllowsRecovery() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "5").statusCode())
        seedTemplates(f, 200, "LEAVE")
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val days = listOf("2026-10-05" to "FULL")
        assertCode(submit(f, id, days, key), 409, "approval_policy_capacity")
        assertEquals("0", balance(f).get("reservedDays").asString())
        val template =
            database()
                .queryForObject(
                    "select id from approval_templates where company_id=? and name='Capacity policy' order by id limit 1",
                    UUID::class.java,
                    f.company,
                )!!
        assertEquals(200, saveTemplate(f, template, templateBody("LEAVE", 0, false)).statusCode())
        val retried = submit(f, id, days, key)
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals("1", balance(f).get("reservedDays").asString())
    }

    @Test
    fun totalTemplateLimitStillAllowsAnExistingDefinitionToBeRepaired() {
        val f = leaveFixture()
        seedTemplates(f, 1000, active = false)
        assertCode(
            saveTemplate(f, UUID.randomUUID(), templateBody(active = false)),
            409,
            "approval_template_limit",
        )
        val id =
            database()
                .queryForObject(
                    "select id from approval_templates where company_id=? order by id limit 1",
                    UUID::class.java,
                    f.company,
                )!!
        val changed = saveTemplate(f, id, templateBody(version = 0))
        assertEquals(200, changed.statusCode(), changed.body())
        val page =
            json.readTree(
                get(
                        f.admin,
                        "/api/v1/companies/${f.company}/approvals/templates?kind=EXPENSE&asOf=2026-10-01&limit=200",
                    )
                    .body()
            )
        assertEquals(200, page.get("items").size())
        assertFalse(page.get("nextCursor").isNull)
        assertCode(
            saveTemplate(
                f,
                id,
                templateBody(version = 1, changes = mapOf("effectiveFrom" to "1800-01-01")),
            ),
            422,
            "invalid_approval_template",
        )
    }

    @Test
    fun delegationCapacityIsSerializedAndRevocationOrExpiryReleasesTheRightCapacity() {
        val f = leaveFixture()
        val to = reviewer(f.company)
        seedDelegations(f, to.account, 199)
        val ids = List(2) { UUID.randomUUID() }
        val body = delegationBody(f, to.account)
        val go = CountDownLatch(1)
        val results =
            Executors.newFixedThreadPool(2).use { pool ->
                val tasks =
                    ids.map { id ->
                        pool.submit<HttpResponse<String>> {
                            check(go.await(5, TimeUnit.SECONDS))
                            saveDelegation(f, id, body)
                        }
                    }
                go.countDown()
                tasks.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 409), results.map { it.statusCode() }.sorted())
        val winner = ids[results.indexOfFirst { it.statusCode() == 200 }]
        val loser = ids[results.indexOfFirst { it.statusCode() == 409 }]
        assertEquals(
            200,
            saveDelegation(f, winner, delegationBody(f, to.account, 0, false)).statusCode(),
        )
        assertEquals(200, saveDelegation(f, loser, body).statusCode())
        assertCode(
            saveDelegation(f, winner, delegationBody(f, to.account, 1)),
            409,
            "approval_delegation_capacity",
        )
        val path = "/api/v1/companies/${f.company}/approvals/delegations"
        val first = json.readTree(get(to.client, "$path?limit=1").body())
        val second =
            json.readTree(
                get(to.client, "$path?limit=1&after=${first.get("nextCursor").asString()}").body()
            )
        assertNotEquals(
            first.get("items")[0].get("id").asString(),
            second.get("items")[0].get("id").asString(),
        )
        assertEquals(422, get(to.client, "$path?limit=201").statusCode())
        seedDelegations(f, to.account, 799, false)
        val inactive = delegationBody(f, to.account, active = false)
        assertCode(saveDelegation(f, UUID.randomUUID(), inactive), 409, "approval_delegation_limit")
        val expired =
            delegationBody(
                f,
                to.account,
                1,
                false,
                mapOf(
                    "validFrom" to clock.instant().minusSeconds(3600).toString(),
                    "validUntil" to clock.instant().minusSeconds(1800).toString(),
                ),
            )
        assertEquals(200, saveDelegation(f, winner, expired).statusCode())
        assertEquals(200, saveDelegation(f, UUID.randomUUID(), inactive).statusCode())
    }

    @Test
    fun oversizedDelegationSetsDoNotPermitTruncatedDecisionsAndCanBeRepaired() {
        val f = leaveFixture()
        val request = pending(f)
        val to = reviewer(f.company)
        seedDelegations(f, to.account, 201)
        val key = UUID.randomUUID()
        assertCode(decideAs(f, request, to, key), 409, "approval_delegation_capacity")
        assertEquals("1", balance(f).get("reservedDays").asString())
        val page =
            json.readTree(
                get(to.client, "/api/v1/companies/${f.company}/approvals/delegations?limit=200")
                    .body()
            )
        assertEquals(200, page.get("items").size())
        assertFalse(page.get("nextCursor").isNull)
        val id = UUID.fromString(page.get("items")[0].get("id").asString())
        assertEquals(
            200,
            saveDelegation(f, id, delegationBody(f, to.account, 0, false)).statusCode(),
        )
        val retried = decideAs(f, request, to, key)
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals("APPROVED", details(f, request).get("status").asString())
    }

    @Test
    fun inboxRequiresCurrentActionPermissionForBothDelegateAndDelegator() {
        val f = leaveFixture()
        val request = pending(f)
        val to = reviewer(f.company)
        val id = UUID.randomUUID()
        assertEquals(200, saveDelegation(f, id, delegationBody(f, to.account)).statusCode())
        val path = "/api/v1/companies/${f.company}/approvals"
        assertEquals(1, json.readTree(get(to.client, path).body()).get("items").size())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.team.approve'",
                f.company,
                f.managerAccount,
            )
        assertEquals(0, json.readTree(get(to.client, path).body()).get("items").size())
        assertEquals(404, get(to.client, "$path/${approvalId(f.company, request)}").statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.team.approve')",
                f.company,
                f.managerAccount,
            )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.approve'",
                f.company,
                to.account,
            )
        assertEquals(0, json.readTree(get(to.client, path).body()).get("items").size())
        assertCode(decideAs(f, request, to), 403, "access_denied")
    }
}
