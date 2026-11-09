package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceReadinessControl
import dev.fajar.hris.collaboration.domain.policies.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceReadinessControlPolicyTest {
    private val control =
        CollaborationSpaceReadinessControl(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1_794_171_600_123L,
            1L,
        )

    @Test
    fun revisionIsAnExactPositiveLongAndCompanyControlsAreRejected() {
        val wire = collaborationSpaceReadinessControlContent(control)
        for (bad in listOf("0", "-1", "01", "+1", "1.0", "9223372036854775808")) {
            assertNull(
                readCollaborationSpaceReadinessControl(wire + ("publication_revision" to bad))
            )
        }
        val last = control.copy(publicationRevision = Long.MAX_VALUE)
        assertEquals(
            last,
            readCollaborationSpaceReadinessControl(collaborationSpaceReadinessControlContent(last)),
        )
        assertNull(
            readCollaborationSpaceReadinessControl(
                (wire - "publication_revision") + ("schema_version" to "2")
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            collaborationSpaceReadinessControlContent(control.copy(publicationRevision = 0))
        }
    }

    @Test
    fun exactRoundTripHasNoMessageOrKeyAndRedactsDiagnostics() {
        val wire = collaborationSpaceReadinessControlContent(control)
        assertEquals(control, readCollaborationSpaceReadinessControl(wire))
        assertEquals(9, wire.size)
        assertEquals(
            control.publishedAtEpochMillis + 86_400_000,
            wire.getValue("expires_at").toLong(),
        )
        assertFalse(control.toString().contains(control.eventId.toString()))
        for (time in listOf(1L, 253_402_214_399_999L)) {
            val edge = control.copy(publishedAtEpochMillis = time)
            assertEquals(
                edge,
                readCollaborationSpaceReadinessControl(
                    collaborationSpaceReadinessControlContent(edge)
                ),
            )
        }
    }

    @Test
    fun malformedOrExtendedEnvelopeCannotBecomeAWake() {
        val wire = collaborationSpaceReadinessControlContent(control)
        for (field in wire.keys) assertNull(readCollaborationSpaceReadinessControl(wire - field))
        for ((name, value) in
            listOf(
                "body" to "unexpected plaintext",
                "schema_version" to "2",
                "schema_version" to "01",
                "published_at" to "01",
                "published_at" to "-1",
                "published_at" to Long.MAX_VALUE.toString(),
                "expires_at" to "0",
                "expires_at" to "0${wire.getValue("expires_at")}",
                "event_id" to "1-1-1-1-1",
                "space_id" to "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
                "native_session_id" to "",
                "device_id" to "*",
            )) assertNull(readCollaborationSpaceReadinessControl(wire + (name to value)), name)
        for (time in listOf(0L, -1L, 253_402_214_400_000L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                collaborationSpaceReadinessControlContent(
                    control.copy(publishedAtEpochMillis = time)
                )
            }
        }
    }

    @Test
    fun zeroIdentityAndCompanyScopeCannotBecomeOrganizationControls() {
        val wire = collaborationSpaceReadinessControlContent(control)
        assertNull(
            readCollaborationSpaceReadinessControl(
                (wire - "space_id") + ("company_id" to control.spaceId.toString())
            )
        )
        for (field in
            listOf("event_id", "space_id", "account_id", "native_session_id", "device_id")) {
            assertNull(
                readCollaborationSpaceReadinessControl(wire + (field to UUID(0, 0).toString()))
            )
        }
        for (value in
            listOf(
                control.copy(eventId = UUID(0, 0)),
                control.copy(spaceId = UUID(0, 0)),
                control.copy(accountId = UUID(0, 0)),
                control.copy(nativeSessionId = UUID(0, 0)),
                control.copy(deviceId = UUID(0, 0)),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                collaborationSpaceReadinessControlContent(value)
            }
        }
        assertNull(readCollaborationReadinessControl(wire))
        assertEquals(
            "dev.fajar.hris.space_recipient_ready",
            COLLABORATION_SPACE_READINESS_CONTROL_TYPE,
        )
    }
}
