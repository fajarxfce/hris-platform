package dev.fajar.hris

import dev.fajar.hris.communications.domain.usecases.PreviewAnnouncementAudience
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AnnouncementAudiencePreviewHttpTest : AnnouncementPublicationApiFixture() {
    @Autowired private lateinit var preview: PreviewAnnouncementAudience

    private fun read(f: Fixture, id: UUID, version: Long = 0) =
        get(f.browser, "${f.path}/$id/audience-preview?expectedVersion=$version")

    @Test
    fun previewIsReadOnlyAndUsesCurrentGroupVersions() {
        val f = fixture()
        val first = member(f)
        val second = member(f)
        val group = group(f, members = listOf(first.employment))
        val id = draft(f, "GROUP", listOf(group))
        val audits = count(f, "audit_entries")
        val result = ok(read(f, id))
        assertEquals(id.toString(), result["announcementId"].asString())
        assertEquals(0, result["version"].asInt())
        assertEquals(1, result["recipientCount"].asInt())
        assertEquals(0, result["audienceVersions"][group.toString()].asInt())
        assertFalse(result.has("recipients"))
        assertEquals(audits, count(f, "audit_entries"))
        assertEquals(0, count(f, "background_jobs"))
        assertEquals(1, count(f, "announcement_revisions"))
        group(f, group, listOf(first.employment, second.employment), 0)
        val changed = ok(read(f, id))
        assertEquals(2, changed["recipientCount"].asInt())
        assertEquals(1, changed["audienceVersions"][group.toString()].asInt())
        group(f, group, emptyList(), 1)
        assertEquals(0, ok(read(f, id))["recipientCount"].asInt())
        group(f, group, emptyList(), 2, active = false)
        error(read(f, id), 422, "announcement_audience_unavailable")
    }

    @Test
    fun eligibilityUsesTheCompanyLocalDateAndTheReadersCurrentAccess() {
        clock.set(Instant.parse("2026-09-30T18:00:00Z"))
        val f = fixture()
        val today = member(f, start = "2026-10-01")
        member(f, start = "2026-10-02")
        val id = draft(f)
        val result = ok(read(f, id))
        assertEquals("2026-10-01", result["asOfDate"].asString())
        assertEquals(clock.instant(), Instant.parse(result["evaluatedAt"].asString()))
        assertEquals(1, result["recipientCount"].asInt())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='announcements.read'",
                f.company,
                today.account,
            )
        assertEquals(0, ok(read(f, id))["recipientCount"].asInt())
    }

    @Test
    fun branchAndDepartmentPreviewsUseEffectiveEmploymentAssignments() {
        val f = fixture()
        val selected = member(f)
        member(f)
        var employmentVersion = 0L
        for (kind in listOf("BRANCH", "DEPARTMENT")) {
            val unit = UUID.randomUUID()
            ok(
                command(
                    f.browser,
                    "/api/v1/companies/${f.company}/organization-units/$unit",
                    json.writeValueAsString(
                        mapOf(
                            "code" to kind,
                            "name" to "Office unit",
                            "kind" to kind,
                            "timezone" to if (kind == "BRANCH") "Asia/Jakarta" else null,
                        )
                    ),
                    f.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            )
            ok(
                revise(
                    f.browser,
                    f.csrf,
                    f.company,
                    selected.employment,
                    employmentVersion++,
                    terms() + mapOf("${kind.lowercase()}Id" to unit),
                )
            )
            val id = draft(f, kind, listOf(unit))
            val result = ok(read(f, id))
            assertEquals(1, result["recipientCount"].asInt())
            assertEquals(0, result["audienceVersions"][unit.toString()].asInt())
        }
    }

    @Test
    fun previewsRequireTheObservedVersionAndDoNotReplaceFrozenPublicationMetadata() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
        val f = fixture()
        member(f)
        val id = draft(f)
        error(read(f, id, -1), 422, "invalid_version")
        error(read(f, id, 1), 409, "stale_version")
        ok(publish(f, id, scheduledFor = clock.instant().plusSeconds(86400)))
        assertEquals(1, ok(read(f, id, 1))["recipientCount"].asInt())
        error(read(f, id), 409, "stale_version")
        val publishedId = draft(f)
        published(f, publishedId)
        error(read(f, publishedId, 2), 409, "announcement_preview_unavailable")
        val archived = draft(f)
        ok(action(f, archived, "archive", 0))
        error(read(f, archived, 1), 409, "announcement_preview_unavailable")
    }

    @Test
    fun managementPermissionAndCompanyIsolationApplyToRecipientCounts() {
        val f = fixture()
        val recipient = member(f)
        val id = draft(f)
        error(
            get(recipient.browser, "${f.path}/$id/audience-preview?expectedVersion=0"),
            403,
            "access_denied",
        )
        val other = fixture()
        error(read(other, id), 404, "announcement_not_found")
    }

    @Test
    fun credentialsAreRecheckedAfterWaitingForTheAccountGuard() {
        val f = fixture()
        member(f)
        val id = draft(f)
        val before = count(f, "audit_entries")
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<*>> { preview.execute(f.actor, id, 0) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        f.actor.accountId,
                    )
            } finally {
                barrier.release.countDown()
            }
            failure(pending.get(15, TimeUnit.SECONDS), "session_revoked")
        }
        assertEquals(before, count(f, "audit_entries"))
        assertEquals(0, count(f, "background_jobs"))
    }
}
