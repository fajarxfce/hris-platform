package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.organization.domain.usecases.*
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, OrganizationReadProbeConfiguration::class)
class OrganizationReadHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var units: OrganizationRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var getUnit: GetOrganizationUnit
    @Autowired private lateinit var listUnits: ListOrganizationUnits
    @Autowired private lateinit var saveUnit: SaveOrganizationUnit
    @Autowired private lateinit var accountProbe: AccountLockProbe
    @Autowired private lateinit var reads: OrganizationReadProbe

    private data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val account: UUID,
        val parent: UUID,
        val child: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/organization-units"
    }

    @AfterEach
    fun releaseProbes() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
        reads.current.getAndSet(null)?.release?.countDown()
    }

    private fun fixture(): Fixture {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val parent = UUID.randomUUID()
        val child = UUID.randomUUID()
        for ((id, payload) in
            listOf(
                parent to
                    mapOf(
                        "code" to "HQ",
                        "name" to "Central Office",
                        "kind" to "BRANCH",
                        "timezone" to "Asia/Jakarta",
                        "active" to true,
                    ),
                child to
                    mapOf(
                        "code" to "RND",
                        "name" to "R&D_100%",
                        "kind" to "DEPARTMENT",
                        "parentId" to parent,
                        "active" to true,
                    ),
            )) {
            val response =
                command(
                    admin,
                    "/api/v1/companies/$company/organization-units/$id",
                    json.writeValueAsString(payload),
                    csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            assertEquals(200, response.statusCode(), response.body())
        }
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Organization fixture',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in listOf("company.read", "company.manage")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        val browser = client()
        return Fixture(
            browser,
            login(browser, "$account@example.test"),
            company,
            account,
            parent,
            child,
        )
    }

    private fun actor(f: Fixture) =
        Actor(
            f.account,
            f.company,
            setOf("company.read", "company.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
        )

    private fun edit(f: Fixture) =
        UnitChange(
            f.child,
            "RND",
            "Revised department",
            UnitKind.DEPARTMENT,
            f.parent,
            null,
            true,
            0,
        )

    private fun error(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }

    private fun <T> whileWaiting(f: Fixture, change: () -> Unit, call: () -> T): T {
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<T> { call() }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun filtersMatchLiteralNamesAndCodesAndTuplePaginationStaysBounded() {
        val f = fixture()
        for (payload in
            listOf(
                mapOf(
                    "code" to "RND-A",
                    "name" to "R&Dx100 cases",
                    "kind" to "DEPARTMENT",
                    "parentId" to f.parent,
                    "active" to true,
                ),
                mapOf(
                    "code" to "OLD",
                    "name" to "Previous Team",
                    "kind" to "DEPARTMENT",
                    "parentId" to f.parent,
                    "active" to false,
                ),
                mapOf(
                    "code" to "ENGINEER",
                    "name" to "Engineer",
                    "kind" to "POSITION",
                    "parentId" to f.child,
                    "active" to true,
                ),
                mapOf(
                    "code" to "SHARED",
                    "name" to "Shared services",
                    "kind" to "COST_CENTER",
                    "active" to true,
                ),
            )) {
            val saved =
                command(
                    f.browser,
                    "${f.path}/${UUID.randomUUID()}",
                    json.writeValueAsString(payload),
                    f.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            assertEquals(200, saved.statusCode(), saved.body())
        }
        for (query in listOf("r&d_100%", "  rnd  ")) {
            val response =
                get(
                    f.browser,
                    "${f.path}?query=${URLEncoder.encode(query, UTF_8)}&kind=DEPARTMENT&active=true",
                )
            assertEquals(200, response.statusCode(), response.body())
            val result = json.readTree(response.body())["items"]
            assertEquals(if (query.contains('%')) 1 else 2, result.size())
            assertEquals("RND", result[0]["code"].asString())
        }
        val inactive = json.readTree(get(f.browser, "${f.path}?active=false").body())["items"]
        assertEquals(1, inactive.size())
        assertEquals("OLD", inactive[0]["code"].asString())
        val injected = get(f.browser, "${f.path}?query=${URLEncoder.encode("' OR 1=1 --", UTF_8)}")
        assertEquals(200, injected.statusCode(), injected.body())
        assertEquals(0, json.readTree(injected.body())["items"].size())
        val seen = mutableSetOf<String>()
        var after: String? = null
        repeat(3) { page ->
            val response =
                get(
                    f.browser,
                    "${f.path}?limit=2${after?.let { "&after=${URLEncoder.encode(it, UTF_8)}" } ?: ""}",
                )
            assertEquals(200, response.statusCode(), response.body())
            val body = json.readTree(response.body())
            val items = body["items"].iterator().asSequence().toList()
            assertEquals(2, items.size)
            for (item in items) assertTrue(seen.add(item["id"].asString()))
            after = body["nextCursor"].takeUnless { it.isNull }?.asString()
            if (page < 2)
                assertEquals(
                    "${items.last()["kind"].asString()}:${items.last()["code"].asString()}",
                    after,
                )
            else assertNull(after)
        }
        assertEquals(6, seen.size)
    }

    @Test
    fun detailsIncludeTheScopedParentAndDoNotResolveForeignUnits() {
        val f = fixture()
        val root = get(f.browser, "${f.path}/${f.parent}")
        assertEquals(200, root.statusCode(), root.body())
        assertEquals(f.company.toString(), json.readTree(root.body())["companyId"].asString())
        assertTrue(json.readTree(root.body())["parent"].isNull)
        val detail = get(f.browser, "${f.path}/${f.child}")
        assertEquals(200, detail.statusCode(), detail.body())
        val body = json.readTree(detail.body())
        assertEquals(f.child.toString(), body["unit"]["id"].asString())
        assertEquals(f.parent.toString(), body["parent"]["id"].asString())
        assertEquals(body["unit"]["parentId"], body["parent"]["id"])
        assertEquals("Central Office", body["parent"]["name"].asString())
        val other = fixture()
        error(get(f.browser, "${f.path}/${other.child}"), 404, "organization_unit_not_found")
        error(get(f.browser, "${other.path}/${other.child}"), 403, "company_access_denied")
        error(get(f.browser, "${f.path}/${UUID.randomUUID()}"), 404, "organization_unit_not_found")
    }

    @Test
    fun invalidPagesAndSearchesUseStableErrorsWithoutEchoingInput() {
        val f = fixture()
        for (query in
            listOf(
                "limit=0",
                "limit=201",
                "after=BRANCH:A",
                "after=OTHER:AB",
                "kind=BRANCH&after=DEPARTMENT:RND",
            )) error(get(f.browser, "${f.path}?$query"), 422, "invalid_page")
        val input = "private-query-".repeat(10)
        val response = get(f.browser, "${f.path}?query=$input")
        error(response, 422, "invalid_organization_search")
        assertFalse(response.body().contains(input))
    }

    @Test
    fun waitingReadsAndWritesRecheckCredentialsMembershipAndOriginalPermissions() {
        for (operation in listOf("get", "list", "save")) {
            for (change in listOf("credential", "membership", "permission")) {
                val f = fixture()
                val actor = actor(f)
                val key = UUID.randomUUID()
                val permission = if (operation == "save") "company.manage" else "company.read"
                val result =
                    whileWaiting(
                        f,
                        {
                            when (change) {
                                "credential" ->
                                    database()
                                        .update(
                                            "update accounts set security_version=security_version+1 where id=?",
                                            f.account,
                                        )
                                "membership" ->
                                    database()
                                        .update(
                                            "update company_memberships set active=false where company_id=? and account_id=?",
                                            f.company,
                                            f.account,
                                        )
                                else ->
                                    database()
                                        .update(
                                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                            f.company,
                                            f.account,
                                            permission,
                                        )
                            }
                        },
                    ) {
                        when (operation) {
                            "get" -> getUnit.execute(actor, f.child)
                            "list" -> listUnits.execute(actor, OrganizationUnitSearch())
                            else -> saveUnit.execute(actor, key, edit(f))
                        }
                    }
                assertEquals(
                    when (change) {
                        "credential" -> "session_revoked"
                        "membership" -> "company_access_denied"
                        else -> "access_denied"
                    },
                    (result as Result.Failed).failure.code,
                )
                assertEquals(
                    0L,
                    database()
                        .queryForObject(
                            "select version from organization_units where id=?",
                            Long::class.java,
                            f.child,
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
            }
        }
        val f = fixture()
        val originallyDenied = actor(f).copy(permissions = emptySet())
        assertEquals(
            "access_denied",
            (getUnit.execute(originallyDenied, f.child) as Result.Failed).failure.code,
        )
        assertEquals(
            "access_denied",
            (listUnits.execute(originallyDenied, OrganizationUnitSearch()) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "access_denied",
            (saveUnit.execute(originallyDenied, UUID.randomUUID(), edit(f)) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun naturalMfaExpiryWhileWaitingRejectsReadsAndMutationsUntilFreshAssurance() {
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val get =
            GetOrganizationUnit(
                units,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val list =
            ListOrganizationUnits(
                units,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val save =
            SaveOrganizationUnit(
                units,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        for (operation in listOf("get", "list", "save")) {
            val f = fixture()
            val actor =
                actor(f)
                    .copy(
                        mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
                    )
            val key = UUID.randomUUID()
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    f.account,
                )
            try {
                val expired =
                    whileWaiting(f, { clock.set(clock.instant().plusSeconds(2)) }) {
                        when (operation) {
                            "get" -> get.execute(actor, f.child)
                            "list" -> list.execute(actor, OrganizationUnitSearch())
                            else -> save.execute(actor, key, edit(f))
                        }
                    }
                assertEquals("mfa_required", (expired as Result.Failed).failure.code)
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from operation_receipts where operation_id=?",
                            Int::class.java,
                            key,
                        ),
                )
                val renewed = actor.copy(mfaVerifiedAt = clock.instant())
                val accepted =
                    when (operation) {
                        "get" -> get.execute(renewed, f.child)
                        "list" -> list.execute(renewed, OrganizationUnitSearch())
                        else -> save.execute(renewed, key, edit(f))
                    }
                assertTrue(accepted is Result.Success, accepted.toString())
                if (operation == "save") {
                    val receipt = (accepted as Result.Success).value as MutationReceipt
                    assertEquals(false, receipt.replayed)
                    assertEquals(
                        Result.Success(receipt.copy(replayed = true)),
                        save.execute(renewed, key, edit(f)),
                    )
                    clock.set(clock.instant().plus(security.maximumMfaAge).plusSeconds(1))
                    assertEquals(
                        "mfa_required",
                        (save.execute(renewed, key, edit(f)) as Result.Failed).failure.code,
                    )
                    assertEquals(
                        1L,
                        database()
                            .queryForObject(
                                "select version from organization_units where id=?",
                                Long::class.java,
                                f.child,
                            ),
                    )
                }
            } finally {
                database()
                    .update("update accounts set mfa_secret_encrypted=null where id=?", f.account)
            }
        }
    }

    @Test
    fun aConcurrentParentEditCannotSplitTheUnitAndParentSnapshot() {
        val f = fixture()
        val barrier = OrganizationReadProbe.Barrier(f.company, f.child)
        reads.current.set(barrier)
        Executors.newFixedThreadPool(2).use { executor ->
            val reading =
                executor.submit<Result<OrganizationUnitDetails>> {
                    getUnit.execute(actor(f), f.child)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val writer =
                    executor.submit<Result<MutationReceipt>> {
                        saveUnit.execute(
                            actor(f),
                            UUID.randomUUID(),
                            UnitChange(
                                f.parent,
                                "HQ",
                                "Renamed Office",
                                UnitKind.BRANCH,
                                null,
                                "Asia/Jakarta",
                                true,
                                0,
                            ),
                        )
                    }
                awaitStructureWriter(f.company)
                barrier.release.countDown()
                val snapshot = (reading.get(10, TimeUnit.SECONDS) as Result.Success).value
                assertEquals(f.parent, snapshot.unit.parentId)
                assertEquals("Central Office", snapshot.parent?.name)
                assertEquals(0L, snapshot.parent?.version)
                assertTrue(writer.get(10, TimeUnit.SECONDS) is Result.Success)
                val fresh = (getUnit.execute(actor(f), f.child) as Result.Success).value
                assertEquals("Renamed Office", fresh.parent?.name)
                assertEquals(1L, fresh.parent?.version)
            } finally {
                barrier.release.countDown()
            }
        }
    }

    private fun awaitStructureWriter(company: UUID) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (System.nanoTime() < deadline) {
            val waiting =
                database()
                    .queryForObject(
                        """
                select exists(select 1 from pg_locks
                    where locktype='advisory' and mode='ExclusiveLock' and not granted
                      and ((classid::bigint << 32) | objid::bigint) = hashtextextended(?,0))
            """,
                        Boolean::class.java,
                        "organization:$company",
                    )
            if (waiting == true) return
            Thread.sleep(10)
        }
        fail<Unit>("The structure writer did not reach its conflicting database guard")
    }

    @Test
    fun cancellationDuringAReadReleasesTheStructureGuardAndCannotReturnLateData() {
        val f = fixture()
        val barrier = OrganizationReadProbe.Barrier(f.company, f.child)
        reads.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<OrganizationUnitDetails>> {
                    getUnit.execute(actor(f), f.child)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(barrier.exited.await(5, TimeUnit.SECONDS))
                assertThrows(CancellationException::class.java) { pending.get(5, TimeUnit.SECONDS) }
            } finally {
                barrier.release.countDown()
            }
        }
        val saved = saveUnit.execute(actor(f), UUID.randomUUID(), edit(f))
        assertTrue(saved is Result.Success, saved.toString())
        val fresh = (getUnit.execute(actor(f), f.child) as Result.Success).value
        assertEquals("Revised department", fresh.unit.name)
        assertEquals(1L, fresh.unit.version)
    }
}
