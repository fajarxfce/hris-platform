package dev.fajar.hris

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.AudienceReferenceRepository
import dev.fajar.hris.communications.domain.usecases.ListAudienceReferences
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AudienceReferenceHttpTest : AnnouncementPublicationApiFixture() {
    @Autowired private lateinit var references: AudienceReferenceRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var list: ListAudienceReferences

    private fun path(f: Fixture, query: String) =
        "/api/v1/companies/${f.company}/communications/audience-references?$query"

    private fun unit(f: Fixture, kind: String, name: String, active: Boolean = true): UUID {
        val id = UUID.randomUUID()
        ok(
            command(
                f.browser,
                "/api/v1/companies/${f.company}/organization-units/$id",
                json.writeValueAsString(
                    mapOf(
                        "code" to "U${id.toString().take(8)}",
                        "name" to name,
                        "kind" to kind,
                        "timezone" to if (kind == "BRANCH") "Asia/Jakarta" else null,
                        "active" to active,
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        return id
    }

    @Test
    fun referencesExposeOnlyTheirBoundedProjectionAndDoNotRequireProfileAccess() {
        val f = fixture()
        val current = employee(f.browser, f.csrf, f.company)
        val future = employee(f.browser, f.csrf, f.company, start = "2027-01-01")
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission<>'announcements.manage'",
                f.company,
                f.actor.accountId,
            )
        val result = ok(get(f.browser, path(f, "kind=EMPLOYMENT")))
        assertEquals(
            setOf(current.toString(), future.toString()),
            result["items"].iterator().asSequence().map { it["id"].asString() }.toList().toSet(),
        )
        assertTrue(result["nextCursor"].isNull)
        for (item in result["items"]) {
            assertEquals(
                setOf("id", "kind", "name", "code", "version", "active"),
                item.propertyNames().toSet(),
            )
            assertTrue(item["active"].isNull)
            assertEquals("EMPLOYMENT", item["kind"].asString())
            assertEquals("Example employee", item["name"].asString())
        }
        error(
            get(f.browser, "/api/v1/companies/${f.company}/employees?asOf=2026-10-01"),
            403,
            "access_denied",
        )
    }

    @Test
    fun ordinarySearchHidesInactiveDefinitionsButSelectedLookupRetainsTheirLabels() {
        val f = fixture()
        val branch = unit(f, "BRANCH", "Active branch")
        val hidden = unit(f, "BRANCH", "Archived branch", active = false)
        unit(f, "DEPARTMENT", "Active department")
        val active = group(f, members = emptyList())
        val archived = group(f, members = emptyList(), active = false)
        val groups = ok(get(f.browser, path(f, "kind=GROUP")))
        assertEquals(
            listOf(active.toString()),
            groups["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        val branches = ok(get(f.browser, path(f, "kind=BRANCH")))
        assertEquals(
            listOf(branch.toString()),
            branches["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        for ((kind, id) in listOf("BRANCH" to hidden, "GROUP" to archived)) {
            val selected = ok(get(f.browser, path(f, "kind=$kind&ids=$id")))
            assertEquals(id.toString(), selected["items"][0]["id"].asString())
            assertFalse(selected["items"][0]["active"].asBoolean())
            assertTrue(selected["nextCursor"].isNull)
        }
    }

    @Test
    fun paginationUsesStableIdsAndSearchTreatsWildcardsAsLiteralCharacters() {
        val f = fixture()
        val ids =
            listOf(
                    unit(f, "BRANCH", "Office %_ South"),
                    unit(f, "BRANCH", "Office South"),
                    unit(f, "BRANCH", "Office North"),
                )
                .map { it.toString() }
                .sorted()
        val first = ok(get(f.browser, path(f, "kind=BRANCH&limit=2")))
        assertEquals(
            ids.take(2),
            first["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        assertEquals(ids[1], first["nextCursor"].asString())
        val last = ok(get(f.browser, path(f, "kind=BRANCH&limit=2&after=${ids[1]}")))
        assertEquals(
            listOf(ids[2]),
            last["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        assertTrue(last["nextCursor"].isNull)
        val encoded = URLEncoder.encode("%_", StandardCharsets.UTF_8)
        val literal = ok(get(f.browser, path(f, "kind=BRANCH&query=$encoded")))
        assertEquals(1, literal["items"].size())
        assertEquals("Office %_ South", literal["items"][0]["name"].asString())
        val upper = ok(get(f.browser, path(f, "kind=BRANCH&query=NORTH")))
        assertEquals(1, upper["items"].size())
    }

    @Test
    fun referenceIdsDoNotRevealForeignCompaniesEvenThroughTheRuntimeRepository() {
        val f = fixture()
        val other = fixture()
        val ids =
            mapOf(
                AudienceReferenceKind.BRANCH to unit(other, "BRANCH", "Foreign branch"),
                AudienceReferenceKind.GROUP to group(other, members = emptyList()),
                AudienceReferenceKind.EMPLOYMENT to
                    employee(other.browser, other.csrf, other.company),
            )
        for ((kind, id) in ids) {
            assertEquals(0, ok(get(f.browser, path(f, "kind=$kind&ids=$id")))["items"].size())
            assertEquals(
                Result.Success(Page<AudienceReference>(emptyList(), null)),
                transactions.run(f.actor) {
                    references.list(other.company, AudienceReferenceSearch(kind), false)
                },
            )
        }
        val recipient = member(f)
        error(get(recipient.browser, path(f, "kind=EMPLOYMENT")), 403, "access_denied")
    }

    @Test
    fun invalidFiltersAreRejectedBeforeReferenceAcquisition() {
        val f = fixture()
        for (limit in listOf(0, 201)) error(
            get(f.browser, path(f, "kind=GROUP&limit=$limit")),
            422,
            "invalid_page_size",
        )
        val id = UUID.randomUUID()
        val tooMany = (1..51).joinToString("&") { "ids=${UUID.randomUUID()}" }
        for (filter in
            listOf(
                "query=${"x".repeat(121)}",
                "query=hello%09world",
                "ids=$id&ids=$id",
                "ids=$id&query=team",
                "ids=$id&after=$id",
                "ids=$id&ids=${UUID.randomUUID()}&limit=1",
                tooMany,
            )) error(
            get(f.browser, path(f, "kind=GROUP&$filter")),
            422,
            "invalid_audience_reference_search",
        )
        assertEquals(400, get(f.browser, path(f, "kind=COMPANY")).statusCode())
    }

    @Test
    fun grantsAndCredentialsAreRecheckedAfterPendingAcquisition() {
        for (revokeCredentials in listOf(false, true)) {
            val f = fixture()
            unit(f, "BRANCH", "Protected reference")
            val barrier = AccountLockProbe.Barrier(f.actor.accountId)
            accountProbe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { executor ->
                    val pending =
                        executor.submit<Result<*>> {
                            list.execute(
                                f.actor,
                                AudienceReferenceSearch(AudienceReferenceKind.BRANCH),
                            )
                        }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        if (revokeCredentials)
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                        else
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='announcements.manage'",
                                    f.company,
                                    f.actor.accountId,
                                )
                    } finally {
                        barrier.release.countDown()
                    }
                    failure(
                        pending.get(15, TimeUnit.SECONDS),
                        if (revokeCredentials) "session_revoked" else "access_denied",
                    )
                }
            } finally {
                accountProbe.current.set(null)
                barrier.release.countDown()
            }
        }
    }

    @Test
    fun expiredAssuranceCannotReleaseLabelsAfterWaitingButFreshVerificationCan() {
        val f = fixture()
        unit(f, "BRANCH", "Verified reference")
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        val security = IdentitySecurityPolicy()
        val secured =
            ListAudienceReferences(references, members, identities, transactions, clock, security)
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val query = AudienceReferenceSearch(AudienceReferenceKind.BRANCH)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<*>> { secured.execute(actor, query) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                } finally {
                    barrier.release.countDown()
                }
                failure(pending.get(15, TimeUnit.SECONDS), "mfa_required")
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
        assertTrue(
            secured.execute(actor.copy(mfaVerifiedAt = clock.instant()), query) is Result.Success
        )
    }
}
