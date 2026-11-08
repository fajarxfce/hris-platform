package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.CollaborationReadinessControl
import dev.fajar.hris.collaboration.domain.policies.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationReadinessControlPolicyTest {
    private val control =
        CollaborationReadinessControl(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1_794_171_600_123L,
            1L,
        )

    @Test
    fun revisionIsAnExactPositiveLongAndLegacyUnorderedControlsAreRejected() {
        val wire = collaborationReadinessControlContent(control)
        for (bad in listOf("0", "-1", "01", "+1", "1.0", "9223372036854775808")) {
            assertNull(readCollaborationReadinessControl(wire + ("publication_revision" to bad)))
        }
        val last = control.copy(publicationRevision = Long.MAX_VALUE)
        assertEquals(
            last,
            readCollaborationReadinessControl(collaborationReadinessControlContent(last)),
        )
        assertNull(
            readCollaborationReadinessControl(
                (wire - "publication_revision") + ("schema_version" to "1")
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            collaborationReadinessControlContent(control.copy(publicationRevision = 0))
        }
    }

    @Test
    fun exactRoundTripHasNoMessageOrKeyAndRedactsDiagnostics() {
        val wire = collaborationReadinessControlContent(control)
        assertEquals(control, readCollaborationReadinessControl(wire))
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
                readCollaborationReadinessControl(collaborationReadinessControlContent(edge)),
            )
        }
    }

    @Test
    fun malformedOrExtendedEnvelopeCannotBecomeAWake() {
        val wire = collaborationReadinessControlContent(control)
        for (field in wire.keys) assertNull(readCollaborationReadinessControl(wire - field))
        for ((name, value) in
            listOf(
                "body" to "unexpected plaintext",
                "schema_version" to "1",
                "schema_version" to "01",
                "published_at" to "01",
                "published_at" to "-1",
                "published_at" to Long.MAX_VALUE.toString(),
                "expires_at" to "0",
                "expires_at" to "0${wire.getValue("expires_at")}",
                "event_id" to "1-1-1-1-1",
                "company_id" to "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
                "native_session_id" to "",
                "device_id" to "*",
            )) assertNull(readCollaborationReadinessControl(wire + (name to value)), name)
        for (time in listOf(0L, -1L, 253_402_214_400_000L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                collaborationReadinessControlContent(control.copy(publishedAtEpochMillis = time))
            }
        }
    }
}
