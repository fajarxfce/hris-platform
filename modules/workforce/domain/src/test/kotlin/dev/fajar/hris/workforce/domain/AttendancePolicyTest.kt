package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttendancePolicyTest {
    private val account = UUID.randomUUID()
    private val employee = UUID.randomUUID()
    private val device = UUID.randomUUID()
    private val now = Instant.parse("2026-10-01T15:00:00Z")
    private val date = LocalDate.parse("2026-10-01")
    private val window =
        AttendanceCaptureWindow(
            UUID.randomUUID(),
            employee,
            account,
            device,
            now,
            now.plusSeconds(120),
        )
    private val shift =
        ShiftSnapshot(
            UUID.randomUUID(),
            0,
            ShiftDetails(
                "NIGHT",
                "Night",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                30,
                "Asia/Jakarta",
                WorkMode.REMOTE,
                false,
                100.0,
                null,
            ),
        )
    private val schedule =
        (scheduledWorkDay(date, shift, CalendarOrigin.ROSTER, 0) as Result.Success).value

    private fun capture(offline: Boolean = false) =
        AttendanceCapture(
            UUID.randomUUID(),
            employee,
            date,
            AttendanceKind.CLOCK_IN,
            now,
            device,
            window.id,
            offline,
            null,
        )

    private fun assessment(
        capture: AttendanceCapture,
        proof: AttendanceCaptureWindow? = window,
        at: Instant = now,
        day: ScheduledDay = schedule,
    ): AttendanceAssessment =
        (assessAttendance(capture, account, proof, day, emptyList(), at) as Result.Success).value

    @Test
    fun explicitOfflineAlwaysRequiresReviewEvenWithFreshServerProof() {
        val assessed = assessment(capture(true))
        assertEquals(AttendanceStatus.PENDING, assessed.status)
        assertTrue(AttendanceIssue.OFFLINE in assessed.issues)
        assertEquals(AttendanceStatus.ACCEPTED, assessment(capture()).status)
    }

    @Test
    fun MissingAndExpiredProofCannotMasqueradeAsOnlineAttendance() {
        val missing = assessment(capture().copy(windowId = null), null)
        assertEquals(setOf(AttendanceIssue.UNVERIFIED_CAPTURE), missing.issues)
        assertEquals(
            AttendanceStatus.PENDING,
            assessment(capture(), at = now.plusSeconds(120)).status,
        )
        assertTrue(
            AttendanceIssue.WINDOW_EXPIRED in
                assessment(capture(), at = now.plusSeconds(120)).issues
        )
        val oldCapture = capture().copy(capturedAt = now.minusSeconds(60))
        assertTrue(AttendanceIssue.UNVERIFIED_CAPTURE in assessment(oldCapture).issues)
    }

    @Test
    fun ProofIsBoundToOwnerDeviceAndOneEvent() {
        assertTrue(
            assessAttendance(capture(), UUID.randomUUID(), window, schedule, emptyList(), now)
                is Result.Failed
        )
        assertTrue(
            assessAttendance(
                capture().copy(deviceId = UUID.randomUUID()),
                account,
                window,
                schedule,
                emptyList(),
                now,
            )
                is Result.Failed
        )
        assertTrue(
            assessAttendance(
                capture(),
                account,
                window.copy(consumedBy = UUID.randomUUID()),
                schedule,
                emptyList(),
                now,
            )
                is Result.Failed
        )
    }

    @Test
    fun LocationEvidenceAndUnscheduledWorkBecomeVisibleReviewReasons() {
        val fence = GeoFence(-6.2, 106.8, 150.0)
        val onsite =
            schedule.copy(
                shift =
                    shift.copy(details = shift.details.copy(locationRequired = true, fence = fence))
            )
        assertTrue(AttendanceIssue.LOCATION_REQUIRED in assessment(capture(), day = onsite).issues)
        val invalid = capture().copy(location = AttendanceLocation(-6.3, 106.8, 300.0, true))
        assertTrue(
            assessment(invalid, day = onsite)
                .issues
                .containsAll(
                    setOf(
                        AttendanceIssue.MOCK_LOCATION,
                        AttendanceIssue.LOW_ACCURACY,
                        AttendanceIssue.OUTSIDE_FENCE,
                    )
                )
        )
        val valid = capture().copy(location = AttendanceLocation(-6.2, 106.8, 10.0, false))
        assertEquals(AttendanceStatus.ACCEPTED, assessment(valid, day = onsite).status)
        assertTrue(
            AttendanceIssue.UNSCHEDULED in
                assessment(capture(), day = ScheduledDay.Unassigned(date)).issues
        )
        assertEquals(0.0, geoDistanceMeters(requireNotNull(valid.location), fence), 0.0001)
    }

    @Test
    fun FutureAndNonFiniteEvidenceIsRejectedWithoutProducingDomainFacts() {
        assertTrue(
            validateAttendanceCapture(capture().copy(capturedAt = now.plusSeconds(31)), now)
                is Result.Failed
        )
        assertTrue(
            validateAttendanceCapture(
                capture().copy(location = AttendanceLocation(Double.NaN, 1.0, 10.0, false)),
                now,
            )
                is Result.Failed
        )
        assertTrue(
            validateAttendanceCapture(capture().copy(workDate = date.minusDays(10)), now)
                is Result.Failed
        )
    }

    @Test
    fun UnverifiedEventsAreExcludedAndVerifiedOvernightPairUsesItsSnapshotBreak() {
        val checkIn = capture(true)
        val checkOut =
            checkIn.copy(
                id = UUID.randomUUID(),
                kind = AttendanceKind.CLOCK_OUT,
                capturedAt = now.plusSeconds(8 * 3600),
            )
        val pending = AttendanceAssessment(AttendanceStatus.PENDING, setOf(AttendanceIssue.OFFLINE))
        val raw =
            listOf(checkIn, checkOut).map {
                AttendanceEntry(
                    it,
                    account,
                    now.plusSeconds(9 * 3600),
                    schedule,
                    pending,
                    AttendanceStatus.PENDING,
                    null,
                    0,
                )
            }
        assertEquals(0, summarizeAttendance(date, raw).acceptedMinutes)
        assertEquals(2, summarizeAttendance(date, raw).pendingCount)
        val verified =
            raw.map {
                it.copy(
                    status = AttendanceStatus.ACCEPTED,
                    version = 1,
                    review =
                        AttendanceReview(
                            UUID.randomUUID(),
                            AttendanceReviewDecision.ACCEPT,
                            now.plusSeconds(10 * 3600),
                            "Supervisor verification",
                        ),
                )
            }
        assertEquals(450, summarizeAttendance(date, verified).acceptedMinutes)
        assertFalse(summarizeAttendance(date, verified).incomplete)
        assertEquals(0, summarizeAttendance(date, listOf(verified[0])).acceptedMinutes)
        assertTrue(summarizeAttendance(date, listOf(verified[0])).incomplete)
        assertTrue(validateAttendanceSequence(checkIn, verified) is Result.Failed)
        assertTrue(validateAttendanceSequence(checkOut, emptyList()) is Result.Failed)
    }
}
