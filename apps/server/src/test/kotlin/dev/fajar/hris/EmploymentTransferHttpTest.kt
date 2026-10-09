package dev.fajar.hris

import java.net.http.HttpClient
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException

@Import(AccountLockProbeConfiguration::class, StructureLockProbeConfiguration::class)
class EmploymentTransferHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var accessProbe: AccountLockProbe
    @Autowired private lateinit var structureProbe: StructureLockProbe

    @Test
    fun transferAndOrganizationChangesAcquireStructureBeforeCompanyGuards() {
        val admin = client()
        val csrf = login(admin)
        val source = company(admin, csrf)
        val target = company(admin, csrf)
        val id = employee(admin, csrf, source)
        val barrier = StructureLockProbe.Barrier(target)
        structureProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> {
                    transfer(admin, csrf, source, id, target, UUID.randomUUID())
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val unit =
                    command(
                        admin,
                        "/api/v1/companies/$target/organization-units/${UUID.randomUUID()}",
                        json.writeValueAsString(
                            mapOf(
                                "code" to "TEAM",
                                "name" to "Team",
                                "kind" to "DEPARTMENT",
                                "active" to true,
                            )
                        ),
                        csrf,
                        UUID.randomUUID(),
                        "PUT",
                    )
                assertEquals(200, unit.statusCode(), unit.body())
                barrier.release.countDown()
                val moved = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, moved.statusCode(), moved.body())
            } finally {
                barrier.release.countDown()
                structureProbe.current.set(null)
            }
        }
    }

    private fun account(): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Transfer fixture',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        return id
    }

    private fun membership(company: UUID, account: UUID, permissions: Set<String>) {
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    account,
                    it,
                )
        }
    }

    private fun transfer(
        browser: HttpClient,
        csrf: String,
        source: UUID,
        id: UUID,
        target: UUID,
        newId: UUID,
        version: Long = 0,
        key: UUID = UUID.randomUUID(),
        number: String = "T${newId.toString().take(8)}",
        targetTerms: Map<String, Any?> = terms("2026-10-01", start = "2026-10-01"),
    ) =
        command(
            browser,
            "/api/v1/companies/$source/employees/$id/transfer",
            json.writeValueAsString(
                mapOf(
                    "targetCompanyId" to target,
                    "targetEmploymentId" to newId,
                    "expectedVersion" to version,
                    "employeeNumber" to number,
                    "terms" to targetTerms,
                    "reason" to "Approved company transfer",
                )
            ),
            csrf,
            key,
        )

    private fun existingEmployment(
        company: UUID,
        person: UUID,
        actor: UUID,
        from: String = "2026-01-01",
    ): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,?)",
                company,
                id,
                person,
                "EX${id.toString().take(8)}",
            )
        database()
            .update(
                """insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason)
            values(?,?,0,?::date,'PERMANENT',?::date,'ACTIVE',?,'Fixture employment')""",
                company,
                id,
                from,
                from,
                actor,
            )
        return id
    }

    @Test
    fun transferPreservesPersonAndHistoryWhileRevokingOnlySourceMembership() {
        val admin = client()
        val csrf = login(admin)
        val source = company(admin, csrf)
        val target = company(admin, csrf)
        val subject = account()
        membership(source, subject, setOf("company.read", "people.self.read"))
        membership(target, subject, setOf("company.read", "people.self.read"))
        val id = employee(admin, csrf, source, account = subject)
        val newId = UUID.randomUUID()
        val key = UUID.randomUUID()
        val person =
            database()
                .queryForObject(
                    "select person_id from employments where company_id=? and id=?",
                    UUID::class.java,
                    source,
                    id,
                )!!
        val user = client()
        login(user, "$subject@example.test")
        val result = transfer(admin, csrf, source, id, target, newId, key = key)
        assertEquals(200, result.statusCode(), result.body())
        assertEquals(
            result.body(),
            transfer(admin, csrf, source, id, target, newId, key = key).body(),
        )
        assertEquals(
            person,
            database()
                .queryForObject(
                    "select person_id from employments where company_id=? and id=?",
                    UUID::class.java,
                    target,
                    newId,
                ),
        )
        assertEquals(
            source,
            database()
                .queryForObject(
                    "select owner_company_id from persons where id=?",
                    UUID::class.java,
                    person,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from persons where account_id=?",
                    Int::class.java,
                    subject,
                ),
        )
        val old = get(admin, "/api/v1/companies/$source/employees/$id?asOf=2026-09-30")
        val ended = get(admin, "/api/v1/companies/$source/employees/$id?asOf=2026-10-01")
        assertEquals("ACTIVE", json.readTree(old.body()).get("terms").get("status").asString())
        assertEquals("ENDED", json.readTree(ended.body()).get("terms").get("status").asString())
        assertEquals(
            "2026-09-30",
            json.readTree(ended.body()).get("terms").get("endDate").asString(),
        )
        assertEquals(403, get(user, "/api/v1/companies/$source/me/access").statusCode())
        assertEquals(200, get(user, "/api/v1/companies/$target/me/access").statusCode())
        assertEquals(
            200,
            get(user, "/api/v1/companies/$target/employees/$newId?asOf=2026-10-01").statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Int::class.java,
                    subject,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from company_memberships where company_id=? and account_id=?",
                    Int::class.java,
                    target,
                    subject,
                ),
        )
        val grant = get(admin, "/api/v1/companies/$source/members/$subject")
        assertEquals(200, grant.statusCode(), grant.body())
        assertTrue(grant.body().contains("people.self.read"))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from membership_role_applications where company_id=? and account_id=? and membership_version=1",
                    Int::class.java,
                    source,
                    subject,
                ),
        )
        assertEquals(
            1,
            json
                .readTree(get(admin, "/api/v1/companies/$source/employees/$id/transfers").body())
                .size(),
        )
        assertEquals(
            1,
            json
                .readTree(get(admin, "/api/v1/companies/$target/employees/$newId/transfers").body())
                .size(),
        )
        val reopened = revise(admin, csrf, source, id, 1, terms("2026-10-02"))
        assertEquals(409, reopened.statusCode(), reopened.body())
        assertEquals(
            "transferred_employment_closed",
            json.readTree(reopened.body()).get("code").asString(),
        )
        assertThrows(DataAccessException::class.java) {
            database().update("delete from employment_transfers where id=?", key)
        }
    }

    @Test
    fun bothCompanyPermissionsAreRequiredAndLiveCredentialRevocationWinsDuringTheCommand() {
        val admin = client()
        val adminCsrf = login(admin)
        val source = company(admin, adminCsrf)
        val target = company(admin, adminCsrf)
        val id = employee(admin, adminCsrf, source)
        val operator = account()
        val grants = setOf("people.read", "people.manage", "people.transfer", "people.offboard")
        membership(source, operator, grants)
        membership(target, operator, setOf("people.read"))
        val browser = client()
        var csrf = login(browser, "$operator@example.test")
        val newId = UUID.randomUUID()
        val key = UUID.randomUUID()
        assertEquals(
            403,
            transfer(browser, csrf, source, id, target, newId, key = key).statusCode(),
        )
        for (permission in grants - setOf("people.read")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                target,
                operator,
                permission,
            )
        val barrier = AccountLockProbe.Barrier(operator)
        accessProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val request =
                executor.submit<Int> {
                    transfer(browser, csrf, source, id, target, newId, key = key).statusCode()
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        operator,
                    )
            } finally {
                barrier.release.countDown()
                accessProbe.current.set(null)
            }
            assertEquals(401, request.get(10, TimeUnit.SECONDS))
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employment_transfers where id=?",
                    Int::class.java,
                    key,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=? and id=?",
                    Int::class.java,
                    target,
                    newId,
                ),
        )
        csrf = login(browser, "$operator@example.test")
        assertEquals(
            200,
            transfer(browser, csrf, source, id, target, newId, key = key).statusCode(),
        )
    }

    @Test
    fun simultaneousTransfersProduceOneDestinationEmploymentAndOneTransferRecord() {
        val browser = client()
        val csrf = login(browser)
        val source = company(browser, csrf)
        val target = company(browser, csrf)
        val id = employee(browser, csrf, source)
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val requests =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        check(go.await(5, TimeUnit.SECONDS))
                        transfer(browser, csrf, source, id, target, UUID.randomUUID()).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertEquals(listOf(200, 409), requests.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    target,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employment_transfers where source_company_id=?",
                    Int::class.java,
                    source,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select version from employments where company_id=? and id=?",
                    Int::class.java,
                    source,
                    id,
                ),
        )
    }

    @Test
    fun failureInTheDestinationAuditRollsBackBothEmploymentsAccessAndReceipt() {
        val browser = client()
        val csrf = login(browser)
        val source = company(browser, csrf)
        val target = company(browser, csrf)
        val subject = account()
        membership(source, subject, setOf("company.read", "people.self.read"))
        membership(target, subject, setOf("company.read", "people.self.read"))
        val id = employee(browser, csrf, source, account = subject)
        val newId = UUID.randomUUID()
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_transfer_audit() returns trigger language plpgsql as ${'$'}${'$'} begin
            if new.resource_id='$newId'::uuid and new.action='people.employee_received' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger transfer_probe before insert on audit_entries for each row execute function fail_transfer_audit()"
            )
        try {
            assertEquals(
                409,
                transfer(browser, csrf, source, id, target, newId, key = key).statusCode(),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select version from employments where company_id=? and id=?",
                        Int::class.java,
                        source,
                        id,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from employments where company_id=? and id=?",
                        Int::class.java,
                        target,
                        newId,
                    ),
            )
            assertEquals(
                true,
                database()
                    .queryForObject(
                        "select active from company_memberships where company_id=? and account_id=?",
                        Boolean::class.java,
                        source,
                        subject,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from membership_role_applications where company_id=? and account_id=?",
                        Int::class.java,
                        source,
                        subject,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from employment_transfers where id=?",
                        Int::class.java,
                        key,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from audit_entries where resource_id=? and action='people.employee_transferred'",
                        Int::class.java,
                        id,
                    ),
            )
        } finally {
            database().execute("drop trigger transfer_probe on audit_entries")
            database().execute("drop function fail_transfer_audit()")
        }
        assertEquals(
            200,
            transfer(browser, csrf, source, id, target, newId, key = key).statusCode(),
        )
    }

    @Test
    fun pendingChangesAndReportingAssignmentsMustBeResolvedBeforeTransfer() {
        val browser = client()
        val csrf = login(browser)
        val source = company(browser, csrf)
        val target = company(browser, csrf)
        val id = employee(browser, csrf, source)
        val newId = UUID.randomUUID()
        assertEquals(200, revise(browser, csrf, source, id, 0, terms("2026-11-01")).statusCode())
        val scheduled = transfer(browser, csrf, source, id, target, newId, version = 1)
        assertEquals(409, scheduled.statusCode())
        assertEquals(
            "scheduled_employment_changes_pending",
            json.readTree(scheduled.body()).get("code").asString(),
        )
        assertEquals(200, cancellation(browser, csrf, source, id, 1, 1).statusCode())
        val report = employee(browser, csrf, source, manager = id)
        val blocked = transfer(browser, csrf, source, id, target, newId, version = 2)
        assertEquals(409, blocked.statusCode())
        assertEquals(
            "reporting_reassignment_required",
            json.readTree(blocked.body()).get("code").asString(),
        )
        assertEquals(
            200,
            revise(browser, csrf, source, report, 0, terms("2026-11-01", manager = id)).statusCode(),
        )
        assertEquals(
            200,
            revise(browser, csrf, source, report, 1, terms("2026-10-01")).statusCode(),
        )
        assertEquals(
            409,
            transfer(browser, csrf, source, id, target, newId, version = 2).statusCode(),
        )
        assertEquals(200, cancellation(browser, csrf, source, report, 2, 1).statusCode())
        assertEquals(
            200,
            transfer(browser, csrf, source, id, target, newId, version = 2).statusCode(),
        )
    }

    @Test
    fun destinationEligibilityAndCompanyDatesAreValidatedBeforeAnyMutation() {
        val browser = client()
        val csrf = login(browser)
        val source = company(browser, csrf)
        val target = company(browser, csrf)
        val subject = account()
        membership(source, subject, setOf("people.self.read"))
        val id = employee(browser, csrf, source, account = subject)
        val newId = UUID.randomUUID()
        val missing = transfer(browser, csrf, source, id, target, newId)
        assertEquals(422, missing.statusCode())
        assertEquals(
            "destination_membership_required",
            json.readTree(missing.body()).get("code").asString(),
        )
        membership(target, subject, setOf("people.self.read"))
        val person =
            database()
                .queryForObject(
                    "select person_id from employments where company_id=? and id=?",
                    UUID::class.java,
                    source,
                    id,
                )!!
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        existingEmployment(target, person, admin)
        val occupied = transfer(browser, csrf, source, id, target, newId)
        assertEquals(409, occupied.statusCode())
        assertEquals(
            "destination_employment_exists",
            json.readTree(occupied.body()).get("code").asString(),
        )
        val foreignDate = company(browser, csrf, "America/Los_Angeles")
        val unlinked = employee(browser, csrf, source)
        val date = transfer(browser, csrf, source, unlinked, foreignDate, UUID.randomUUID())
        assertEquals(422, date.statusCode())
        assertEquals(
            "transfer_requires_current_company_date",
            json.readTree(date.body()).get("code").asString(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from employments where company_id=? and id=?",
                    Int::class.java,
                    source,
                    id,
                ),
        )
    }

    @Test
    fun otherCurrentOrPlannedEmploymentKeepsSourceMembershipActive() {
        val browser = client()
        val csrf = login(browser)
        val source = company(browser, csrf)
        val target = company(browser, csrf)
        val subject = account()
        membership(source, subject, setOf("company.read", "people.self.read"))
        membership(target, subject, setOf("company.read", "people.self.read"))
        val id = employee(browser, csrf, source, account = subject)
        val newId = UUID.randomUUID()
        val person =
            database()
                .queryForObject(
                    "select person_id from employments where company_id=? and id=?",
                    UUID::class.java,
                    source,
                    id,
                )!!
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        existingEmployment(source, person, admin, "2027-01-01")
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='people.offboard'",
                source,
                admin,
            )
        val result = transfer(browser, csrf, source, id, target, newId)
        assertEquals(200, result.statusCode(), result.body())
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    source,
                    subject,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from company_memberships where company_id=? and account_id=?",
                    Int::class.java,
                    source,
                    subject,
                ),
        )
    }

    @Test
    fun selfAndLastAdministratorProtectionDoNotDependOnAPaginatedDirectory() {
        val browser = client()
        val csrf = login(browser)
        val source = company(browser, csrf)
        val target = company(browser, csrf)
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        val own = employee(browser, csrf, source, account = admin)
        assertEquals(
            409,
            transfer(browser, csrf, source, own, target, UUID.randomUUID()).statusCode(),
        )
        val subject = account()
        membership(source, subject, setOf("identity.manage", "people.self.read"))
        membership(target, subject, setOf("people.self.read"))
        val id = employee(browser, csrf, source, account = subject)
        val newId = UUID.randomUUID()
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='identity.manage'",
                source,
                admin,
            )
        val last = transfer(browser, csrf, source, id, target, newId)
        assertEquals(409, last.statusCode())
        assertEquals(
            "last_company_administrator",
            json.readTree(last.body()).get("code").asString(),
        )
        database()
            .execute(
                """insert into accounts(id,email,display_name,active) select ('00000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
            'inactive-'||i||'@example.test','Inactive administrator',false from generate_series(1,201) i"""
            )
        database()
            .update(
                """insert into company_memberships(company_id,account_id) select ?,id from accounts where email like 'inactive-%@example.test'""",
                source,
            )
        database()
            .update(
                """insert into membership_permissions(company_id,account_id,permission) select ?,id,'identity.manage' from accounts where email like 'inactive-%@example.test'""",
                source,
            )
        val other = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffff01")
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,'other-admin@example.test','Active administrator')",
                other,
            )
        membership(source, other, setOf("identity.manage"))
        assertEquals(200, transfer(browser, csrf, source, id, target, newId).statusCode())
    }
}
