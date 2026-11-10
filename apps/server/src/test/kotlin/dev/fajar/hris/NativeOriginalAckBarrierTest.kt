package dev.fajar.hris

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class NativeOriginalAckBarrierTest {
    @Test
    fun defaultExperimentStillRejectsAnyThirdOriginal() {
        val barrier = NativeOriginalAckBarrier()
        barrier.arm()
        barrier.releaseSecond()
        send(barrier, "first")
        send(barrier, "second")
        assertThrows(IllegalStateException::class.java) { send(barrier, "third") }
    }

    @Test
    fun mixedExperimentAllowsThreeExactOriginalsAndTheirRetriesButNoFourth() {
        val barrier = NativeOriginalAckBarrier()
        barrier.arm(3)
        barrier.releaseSecond()
        for (id in listOf("first", "second", "third", "first", "second", "third")) send(barrier, id)
        assertThrows(IllegalStateException::class.java) { send(barrier, "fourth") }
    }

    @Test
    fun invalidCountAndReusingAnArmedExperimentAreRejected() {
        for (count in listOf(-1, 0, 1, 4, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeOriginalAckBarrier().arm(count)
            }
        }
        val barrier = NativeOriginalAckBarrier()
        barrier.arm(3)
        assertThrows(IllegalStateException::class.java) { barrier.arm(3) }
        barrier.releaseSecond()
        assertThrows(IllegalStateException::class.java) { barrier.arm() }
    }

    private fun send(barrier: NativeOriginalAckBarrier, transaction: String) {
        var delivered = false
        barrier.doFilter(
            MockHttpServletRequest(
                "PUT",
                "/_matrix/client/v3/rooms/fictional/send/m.room.encrypted/$transaction",
            ),
            MockHttpServletResponse(),
            FilterChain { _, _ -> delivered = true },
        )
        assertTrue(delivered)
    }
}
