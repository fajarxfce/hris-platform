package dev.fajar.hris.jobs.domain

import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.policies.validJobProgress
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JobProgressPolicyTest {
    private val request =
        JobRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            JobKind.DOCUMENT_INVENTORY,
            UUID.randomUUID(),
            emptyMap(),
            Instant.EPOCH,
            0,
            UUID.randomUUID(),
            Instant.EPOCH,
            100,
        )

    @Test
    fun finiteSourcesCanFinishBeforeTheirSafetyLimitWithoutChangingFixedTotals() {
        assertFalse(validJobProgress(request, 1, JobStep(2, true)))
        assertTrue(validJobProgress(request, 1, JobStep(100, true)))
        assertTrue(
            validJobProgress(
                request.copy(progressMode = JobProgressMode.UPPER_BOUND),
                1,
                JobStep(2, true),
            )
        )
        assertTrue(
            validJobProgress(
                request.copy(progressMode = JobProgressMode.UPPER_BOUND),
                0,
                JobStep(0, true),
            )
        )
    }

    @Test
    fun NeitherModeCanLoopWithoutProgressGoBackwardsOrExceedTheBound() {
        for (mode in JobProgressMode.entries) {
            val job = request.copy(progressMode = mode)
            assertFalse(validJobProgress(job, 2, JobStep(2, false)))
            assertFalse(validJobProgress(job, 2, JobStep(1, true)))
            assertFalse(validJobProgress(job, 2, JobStep(101, true)))
            assertTrue(validJobProgress(job, 2, JobStep(3, false)))
        }
    }
}
