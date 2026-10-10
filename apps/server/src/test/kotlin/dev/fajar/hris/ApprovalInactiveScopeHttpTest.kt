package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApprovalInactiveScopeHttpTest : ApprovalApiFixture() {
    @Test
    fun inactiveDelegationCannotUseAForeignRecipientAndARejectedOperationCanBeCorrected() {
        val f = leaveFixture()
        val local = reviewer(f.company)
        val foreign = reviewer(leaveFixture().company)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        assertCode(
            saveDelegation(f, id, delegationBody(f, foreign.account, active = false), key),
            422,
            "approver_unavailable",
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from approval_delegations where company_id=? and id=?",
                    Int::class.java,
                    f.company,
                    id,
                ),
        )
        val corrected = saveDelegation(f, id, delegationBody(f, local.account, active = false), key)
        assertEquals(200, corrected.statusCode(), corrected.body())
        assertEquals(0, json.readTree(corrected.body()).get("version").asInt())
    }

    @Test
    fun administratorCannotCreateAnInactiveDelegationForAForeignDelegator() {
        val f = leaveFixture()
        val local = reviewer(f.company)
        val foreign = reviewer(leaveFixture().company)
        val id = UUID.randomUUID()
        val response =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/approvals/delegations/$id",
                delegationBody(
                    f,
                    local.account,
                    active = false,
                    changes = mapOf("fromAccount" to foreign.account),
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertCode(response, 422, "approver_unavailable")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from approval_delegations where company_id=? and id=?",
                    Int::class.java,
                    f.company,
                    id,
                ),
        )
    }

    @Test
    fun inactiveTemplatesKeepNamedAccountsWithinTheCompanyAndRollbackRejectedChanges() {
        val f = leaveFixture()
        val local = reviewer(f.company, listOf("approvals.read", "expenses.approve"))
        val foreign = reviewer(leaveFixture().company, listOf("approvals.read", "expenses.approve"))
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val invalid =
            templateBody(
                active = false,
                changes =
                    mapOf(
                        "stages" to
                            listOf(
                                mapOf(
                                    "assignment" to "NAMED",
                                    "accountIds" to listOf(foreign.account),
                                )
                            )
                    ),
            )
        assertCode(saveTemplate(f, id, invalid, key), 422, "approver_unavailable")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from approval_templates where company_id=? and id=?",
                    Int::class.java,
                    f.company,
                    id,
                ),
        )
        val valid =
            templateBody(
                active = false,
                changes =
                    mapOf(
                        "stages" to
                            listOf(
                                mapOf(
                                    "assignment" to "NAMED",
                                    "accountIds" to listOf(local.account),
                                )
                            )
                    ),
            )
        val corrected = saveTemplate(f, id, valid, key)
        assertEquals(200, corrected.statusCode(), corrected.body())
        assertEquals(0, json.readTree(corrected.body()).get("version").asInt())
    }

    @Test
    fun revokedLocalMembershipAndAccountDoNotPreventDeactivationOfExistingRules() {
        val f = leaveFixture()
        val to = reviewer(f.company, listOf("approvals.read", "leave.approve", "expenses.approve"))
        val delegation = UUID.randomUUID()
        val template = UUID.randomUUID()
        val stages =
            mapOf(
                "stages" to
                    listOf(mapOf("assignment" to "NAMED", "accountIds" to listOf(to.account)))
            )
        assertEquals(200, saveDelegation(f, delegation, delegationBody(f, to.account)).statusCode())
        assertEquals(200, saveTemplate(f, template, templateBody(changes = stages)).statusCode())
        database()
            .update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                f.company,
                to.account,
            )
        database().update("update accounts set active=false where id=?", to.account)
        val stoppedDelegation =
            saveDelegation(f, delegation, delegationBody(f, to.account, 0, false))
        assertEquals(200, stoppedDelegation.statusCode(), stoppedDelegation.body())
        val stoppedTemplate =
            saveTemplate(f, template, templateBody(version = 0, active = false, changes = stages))
        assertEquals(200, stoppedTemplate.statusCode(), stoppedTemplate.body())
        assertEquals(1, json.readTree(stoppedDelegation.body()).get("version").asInt())
        assertEquals(1, json.readTree(stoppedTemplate.body()).get("version").asInt())
        assertCode(
            saveDelegation(f, delegation, delegationBody(f, to.account, 1, true)),
            422,
            "approver_unavailable",
        )
        assertCode(
            saveTemplate(f, template, templateBody(version = 1, active = true, changes = stages)),
            422,
            "approver_unavailable",
        )
    }
}
