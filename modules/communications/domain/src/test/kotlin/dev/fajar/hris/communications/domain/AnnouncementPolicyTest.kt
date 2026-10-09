package dev.fajar.hris.communications.domain

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementPolicyTest {
    private val id = UUID.randomUUID()

    private fun draft(audience: AnnouncementAudience = AnnouncementAudience(AudienceKind.COMPANY)) =
        SaveAnnouncementCommand(
            id,
            null,
            "Office information",
            "Updated opening hours.\nPlease review.",
            audience,
            true,
            "Schedule update",
        )

    @Test
    fun nonCompanyAudienceCannotSilentlyBecomeCompanyWide() {
        assertEquals(Result.Success(Unit), validateAnnouncementInput(draft()))
        for (kind in listOf(AudienceKind.BRANCH, AudienceKind.DEPARTMENT, AudienceKind.GROUP)) {
            val empty = validateAnnouncementInput(draft(AnnouncementAudience(kind)))
            assertEquals(
                "invalid_selection",
                (empty as Result.Failed).failure.fields["audience.targetIds"],
            )
            assertTrue(
                validateAnnouncementInput(draft(AnnouncementAudience(kind, listOf(id, id))))
                    is Result.Failed
            )
        }
        assertTrue(
            validateAnnouncementInput(draft(AnnouncementAudience(AudienceKind.COMPANY, listOf(id))))
                is Result.Failed
        )
        assertTrue(
            validateAnnouncementInput(
                draft(AnnouncementAudience(AudienceKind.BRANCH, List(33) { UUID.randomUUID() }))
            )
                is Result.Failed
        )
    }

    @Test
    fun multilineUnicodeIsPreservedButControlCharactersAndUnboundedTextAreRejected() {
        val source = draft().copy(title = "Informasi Élodie — Jakarta", body = "日本語\nJadwal\t09:00")
        assertEquals(Result.Success(Unit), validateAnnouncementInput(source))
        assertTrue(
            validateAnnouncementInput(source.copy(title = "Hidden\nheading")) is Result.Failed
        )
        assertTrue(
            validateAnnouncementInput(source.copy(body = "hidden\u0000text")) is Result.Failed
        )
        assertTrue(
            validateAnnouncementInput(source.copy(body = "a".repeat(16001))) is Result.Failed
        )
        assertTrue(validateAnnouncementInput(source.copy(title = " ")) is Result.Failed)
    }

    @Test
    fun audienceReferencesMustBeActiveAndOfTheRequestedKind() {
        val unit = OrganizationUnit(id, "HQ", "Head office", UnitKind.BRANCH, null, null, true, 0)
        assertEquals(Result.Success(Unit), validateAnnouncementUnit(AudienceKind.BRANCH, unit))
        assertTrue(validateAnnouncementUnit(AudienceKind.DEPARTMENT, unit) is Result.Failed)
        assertTrue(
            validateAnnouncementUnit(AudienceKind.BRANCH, unit.copy(active = false))
                is Result.Failed
        )
        assertTrue(validateAnnouncementUnit(AudienceKind.BRANCH, null) is Result.Failed)
        val group = AudienceGroupSummary(id, 0, "Office", true, 0, Instant.EPOCH)
        val audience = AnnouncementAudience(AudienceKind.GROUP, listOf(id))
        assertEquals(Result.Success(Unit), validateAnnouncementGroups(audience, listOf(group)))
        assertTrue(validateAnnouncementGroups(audience, emptyList()) is Result.Failed)
        assertTrue(
            validateAnnouncementGroups(audience, listOf(group.copy(active = false)))
                is Result.Failed
        )
    }

    @Test
    fun groupMembershipHasAnExplicitBoundAndRejectsDuplicateEmploymentIds() {
        val input = SaveAudienceGroupCommand(id, null, "Office", true, emptyList(), "Configuration")
        assertEquals(Result.Success(Unit), validateAudienceGroupInput(input))
        val ids = List(5000) { UUID(0, it.toLong() + 1) }
        assertEquals(
            Result.Success(Unit),
            validateAudienceGroupInput(input.copy(employmentIds = ids)),
        )
        assertTrue(
            validateAudienceGroupInput(input.copy(employmentIds = ids + UUID.randomUUID()))
                is Result.Failed
        )
        assertTrue(
            validateAudienceGroupInput(input.copy(employmentIds = listOf(id, id))) is Result.Failed
        )
    }
}
