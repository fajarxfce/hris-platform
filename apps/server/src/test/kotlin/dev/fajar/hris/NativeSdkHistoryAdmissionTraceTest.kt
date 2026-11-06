package dev.fajar.hris

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class NativeSdkHistoryAdmissionTraceTest {
    @Test
    fun cachedWindowsStillRequireFreshAdmissionAroundEveryHeadIncludingRecovery() {
        val trace = NativeSdkGatewayTrace()
        repeat(2) { admit(trace) }
        send(trace)
        admit(trace)
        repeat(2) {
            head(trace)
            admit(trace)
        }
        assertTrue(trace.admittedHistoryHeads())
        assertTrue(trace.applicationAdmittedAroundHistory())
    }

    @Test
    fun staleAdmissionBetweenHeadsCannotSatisfyTheTrace() {
        val trace = NativeSdkGatewayTrace()
        repeat(2) { admit(trace) }
        send(trace)
        admit(trace)
        repeat(2) { head(trace) }
        admit(trace)
        assertFalse(trace.admittedHistoryHeads())
    }

    @Test
    fun recoveryRequiresItsOwnHeadAndAdmissionAfterIt() {
        val trace = NativeSdkGatewayTrace()
        repeat(2) { admit(trace) }
        send(trace)
        admit(trace)
        head(trace)
        admit(trace)
        assertFalse(trace.admittedHistoryHeads())
        assertFalse(trace.applicationAdmittedAroundHistory())
        head(trace)
        assertFalse(trace.admittedHistoryHeads())
        assertFalse(trace.applicationAdmittedAroundHistory())
        admit(trace, status = 403)
        assertFalse(trace.admittedHistoryHeads())
        assertFalse(trace.applicationAdmittedAroundHistory())
    }

    @Test
    fun providerPagesAndAnchoredWindowsDoNotCountAsRecoveryHead() {
        val trace = NativeSdkGatewayTrace()
        repeat(2) { admit(trace) }
        send(trace)
        admit(trace)
        repeat(2) {
            head(trace, limit = "20")
            request(
                trace,
                MockHttpServletRequest("GET", "/_matrix/client/v3/rooms/fictional/context/event"),
            )
            admit(trace)
        }
        assertFalse(trace.admittedHistoryHeads())
        assertFalse(trace.applicationAdmittedAroundHistory())
    }

    @Test
    fun historyCannotReuseTheAdmissionThatAllowedTheOriginalSend() {
        val trace = NativeSdkGatewayTrace()
        repeat(2) { admit(trace) }
        send(trace)
        repeat(2) {
            head(trace)
            admit(trace)
        }
        assertFalse(trace.admittedHistoryHeads())
    }

    @Test
    fun providerPagesFollowHistoryReadmissionAndTheirOwnAdmission() {
        val trace = NativeSdkGatewayTrace()
        repeat(2) { admit(trace) }
        send(trace)
        admit(trace)
        head(trace)
        admit(trace)
        admit(trace)
        for (position in listOf(null, "fixture", null)) {
            head(trace, limit = "20", position = position)
            admit(trace)
        }
        assertTrue(trace.admittedProviderPages())
    }

    @Test
    fun providerCannotStartWithOnlyThePreviousWindowReadmission() {
        val trace = NativeSdkGatewayTrace()
        repeat(2) { admit(trace) }
        send(trace)
        admit(trace)
        head(trace)
        admit(trace)
        for (position in listOf(null, "fixture", null)) {
            head(trace, limit = "20", position = position)
            admit(trace)
        }
        assertFalse(trace.admittedProviderPages())
    }

    private fun send(trace: NativeSdkGatewayTrace) {
        trace.allowEncryptedSends()
        request(
            trace,
            MockHttpServletRequest(
                "PUT",
                "/_matrix/client/v3/rooms/fictional/send/m.room.encrypted/transaction",
            ),
        )
        assertTrue(trace.applicationAdmittedBeforeSend())
    }

    private fun admit(trace: NativeSdkGatewayTrace, status: Int = 200) {
        val root = "/api/v1/companies/00000000-0000-4000-8000-000000000001/collaboration/"
        val conversation = "direct-conversations/00000000-0000-4000-8000-000000000002"
        for (route in listOf("native-device", conversation, "$conversation/room")) {
            val request = MockHttpServletRequest("GET", root + route)
            request.addHeader("X-HRIS-Sender-Proof", "1")
            request(trace, request, status)
        }
    }

    private fun head(trace: NativeSdkGatewayTrace, limit: String = "1", position: String? = null) {
        val request = MockHttpServletRequest("GET", "/_matrix/client/v3/rooms/fictional/messages")
        request.addParameter("dir", "b")
        request.addParameter("limit", limit)
        position?.let { request.addParameter("from", it) }
        request(trace, request)
    }

    private fun request(
        trace: NativeSdkGatewayTrace,
        request: MockHttpServletRequest,
        status: Int = 200,
    ) {
        trace.doFilter(request, MockHttpServletResponse()) { _, response ->
            (response as jakarta.servlet.http.HttpServletResponse).status = status
        }
    }
}
