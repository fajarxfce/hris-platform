package dev.fajar.hris

import java.util.Base64
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class NativeSdkKeyShapeTest {
    @Test
    fun earlyEncryptedRequestsAreCountedAtArrivalAndRemainVisibleAfterAdmission() {
        val trace = NativeSdkGatewayTrace()
        val request =
            MockHttpServletRequest(
                "PUT",
                "/_matrix/client/v3/rooms/fictional/send/m.room.encrypted/transaction",
            )
        trace.doFilter(request, MockHttpServletResponse()) { _, _ ->
            assertEquals(1, trace.earlyEncryptedSendCount())
        }
        trace.allowEncryptedSends()
        trace.doFilter(request, MockHttpServletResponse()) { _, _ -> Unit }
        assertEquals(1, trace.earlyEncryptedSendCount())
    }

    @Test
    fun keyDiagnosticReportsOnlyBoundedShapeFacts() {
        val key = Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32))
        val signature = Base64.getEncoder().withoutPadding().encodeToString(ByteArray(64))
        fun signed(fallback: Boolean) =
            """{"key":"$key","fallback":$fallback,"signatures":{"@fictional:example.test":{"ed25519:fixture":"$signature"}}}"""
        val body =
            """{"one_time_keys":{"signed_curve25519:shared":${signed(false)}},"fallback_keys":{"signed_curve25519:shared":${signed(true)}}}"""
        val shape = nativeSdkKeyShape(body.toByteArray())
        assertEquals(true, shape["parsed"])
        assertEquals(1, shape["overlappingIds"])
        assertEquals(1, shape["oneTimeCount"])
        assertEquals(1, shape["fallbackCount"])
        assertTrue(shape.values.all { it is Boolean || it is Int })
        assertFalse(shape.toString().contains(key))
        assertFalse(shape.toString().contains("fictional"))
    }

    @Test
    fun malformedOrOversizedBodiesDoNotEscapeTheDiagnosticBoundary() {
        for (body in
            listOf(byteArrayOf(), "private-fixture".toByteArray(), ByteArray(65_537))) assertEquals(
            mapOf("parsed" to false),
            nativeSdkKeyShape(body),
        )
    }
}
