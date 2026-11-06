package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.UriUtils

/** Bounded, code-only observations: never credentials, IDs, cursor/filter values or bodies. */
@Order(Ordered.HIGHEST_PRECEDENCE)
class NativeSdkGatewayTrace : OncePerRequestFilter() {
    private val observations = mutableListOf<Map<String, Any>>()
    private val encryptedSendAllowed = AtomicBoolean()
    private val earlyEncryptedSends = AtomicInteger()
    private val acceptedEncryptedSends = AtomicInteger()
    private val rejectNextEncrypted = AtomicBoolean()
    private val rejectedEncryptedSends = AtomicInteger()
    private val heldRoom = AtomicReference<String?>()
    private val heldRoomSends = AtomicInteger()
    private val applicationDeviceReads = AtomicInteger()
    private val applicationSummaryReads = AtomicInteger()
    private val applicationDirectoryReads = AtomicInteger()
    private val applicationRecoveryReads = AtomicInteger()
    private val applicationSummaryPages = AtomicInteger()
    private val applicationPrioritySummaries = AtomicInteger()
    private val applicationConversationReads = AtomicInteger()
    private val applicationRoomReads = AtomicInteger()
    private val applicationAdmissionFailures = AtomicInteger()
    private val applicationBeforeSend = AtomicInteger(-1)
    private val headReads = AtomicInteger()
    private val headAdmissions = mutableListOf<Map<String, Int>>()
    private val applicationAtHistory = AtomicReference<Map<String, Int>?>(null)
    private val providerPages = AtomicInteger()
    private val providerAdmissions = mutableListOf<Map<String, Int>>()
    private val providerPositions = mutableListOf<Boolean>()

    fun admittedProviderPages(): Boolean {
        val (pages, positions) =
            synchronized(providerAdmissions) {
                providerAdmissions.toList() to providerPositions.toList()
            }
        val before = pages.firstOrNull() ?: return false
        val after = applicationAdmissions()
        return providerPages.get() in 3..4 &&
            pages.size == providerPages.get() &&
            positions.size == pages.size &&
            !positions.first() &&
            !positions.last() &&
            positions.drop(1).dropLast(1).all { it } &&
            before.getValue("failures") == 0 &&
            after.getValue("failures") == 0 &&
            pages.all { it.getValue("failures") == 0 } &&
            listOf("device", "conversation", "room").all {
                before.getValue(it) >= 6 &&
                    after.getValue(it) > pages.last().getValue(it) &&
                    pages.zipWithNext().all { (a, b) -> b.getValue(it) > a.getValue(it) }
            }
    }

    fun applicationSummaryReadCount() = applicationSummaryReads.get()

    fun prioritySummaryReadCount() = applicationPrioritySummaries.get()

    fun incomingAdmissions() =
        mapOf(
            "directory" to applicationDirectoryReads.get(),
            "admission" to applicationRecoveryReads.get(),
            "summaries" to applicationSummaryPages.get(),
        )

    fun applicationAdmissions() =
        mapOf(
            "device" to applicationDeviceReads.get(),
            "conversation" to applicationConversationReads.get(),
            "room" to applicationRoomReads.get(),
            "failures" to applicationAdmissionFailures.get(),
        )

    fun admittedHistoryHeads(): Boolean {
        val heads = synchronized(headAdmissions) { headAdmissions.toList() }
        val before = heads.firstOrNull() ?: return false
        val after = applicationAdmissions()
        return headReads.get() == 2 &&
            heads.size == 2 &&
            heads.all { it.getValue("failures") == 0 } &&
            after.getValue("failures") == 0 &&
            listOf("device", "conversation", "room").all {
                before.getValue(it) >= 4 &&
                    heads.last().getValue(it) > before.getValue(it) &&
                    after.getValue(it) > heads.last().getValue(it)
            }
    }

    fun applicationAdmittedBeforeSend() = applicationBeforeSend.get() == 1

    fun applicationAdmittedAroundHistory(): Boolean {
        val before = applicationAtHistory.get() ?: return false
        val after = applicationAdmissions()
        return before.getValue("failures") == 0 &&
            after.getValue("failures") == 0 &&
            listOf("device", "conversation", "room").all {
                before.getValue(it) >= 4 && after.getValue(it) > before.getValue(it)
            }
    }

    fun holdRoom(roomId: String) {
        require(roomId.startsWith('!') && roomId.length in 2..255)
        check(heldRoom.compareAndSet(null, roomId))
    }

    fun heldRoomSendCount() = heldRoomSends.get()

    private val syncFaultLock = Any()
    private var syncFaultArmed = false
    private var failedCursor: ByteArray? = null
    private var cursorRetrySucceeded = false

    /** Test-only one-shot fault, armed after actual process death and store restoration. */
    fun interruptNextRetainedSync() =
        synchronized(syncFaultLock) {
            check(!syncFaultArmed && failedCursor == null)
            syncFaultArmed = true
        }

    fun syncInterrupted() = synchronized(syncFaultLock) { failedCursor != null }

    fun retainedSyncRecovered() = synchronized(syncFaultLock) { cursorRetrySucceeded }

    fun allowEncryptedSends() {
        encryptedSendAllowed.set(true)
    }

    fun earlyEncryptedSendCount() = earlyEncryptedSends.get()

    fun acceptedEncryptedSendCount() = acceptedEncryptedSends.get()

    fun rejectNextEncryptedSend() {
        check(rejectNextEncrypted.compareAndSet(false, true))
    }

    fun rejectedEncryptedSendCount() = rejectedEncryptedSends.get()

    private fun recordApplicationAdmission(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (request.getHeader("X-HRIS-Sender-Proof") != "1") return
        val root = "/api/v1/companies/[0-9a-f-]{36}/collaboration/"
        val counter =
            when {
                request.requestURI == "/api/v1/collaboration/native-devices" ->
                    applicationDirectoryReads
                request.requestURI.matches(Regex(root + "native-device/admission")) ->
                    applicationRecoveryReads
                request.requestURI.matches(Regex(root + "direct-conversations/summary-by-room")) ->
                    applicationPrioritySummaries
                request.requestURI.matches(Regex(root + "direct-conversations/summaries")) ->
                    applicationSummaryPages
                request.requestURI.matches(Regex(root + "native-device")) -> applicationDeviceReads
                request.requestURI.matches(
                    Regex(root + "direct-conversations/[0-9a-f-]{36}/summary")
                ) -> applicationSummaryReads
                request.requestURI.matches(Regex(root + "direct-conversations/[0-9a-f-]{36}")) ->
                    applicationConversationReads
                request.requestURI.matches(
                    Regex(root + "direct-conversations/[0-9a-f-]{36}/room")
                ) -> applicationRoomReads
                else -> null
            }
        if (counter == null || request.method != "GET" || response.status != 200)
            applicationAdmissionFailures.updateAndGet { minOf(it + 1, 64) }
        else counter.updateAndGet { minOf(it + 1, 64) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (
            request.method == "GET" &&
                request.requestURI.matches(Regex("/_matrix/client/v3/rooms/[^/]+/messages")) &&
                request.parameterMap.keys in
                    listOf(setOf("dir", "limit"), setOf("dir", "limit", "from")) &&
                request.getParameter("dir") == "b" &&
                request.getParameter("limit") == "20"
        ) {
            providerPages.updateAndGet { minOf(it + 1, 64) }
            synchronized(providerAdmissions) {
                if (providerAdmissions.size < 8) {
                    providerAdmissions.add(applicationAdmissions())
                    providerPositions.add(request.parameterMap.containsKey("from"))
                }
            }
        }
        if (
            request.method == "GET" &&
                request.requestURI.matches(Regex("/_matrix/client/v3/rooms/[^/]+/messages")) &&
                request.parameterMap.keys == setOf("dir", "limit") &&
                request.getParameter("dir") == "b" &&
                request.getParameter("limit") == "1"
        ) {
            headReads.updateAndGet { minOf(it + 1, 64) }
            synchronized(headAdmissions) {
                if (headAdmissions.size < 4) headAdmissions.add(applicationAdmissions())
            }
        }
        if (
            request.method == "GET" &&
                request.requestURI.matches(Regex("/_matrix/client/v3/rooms/[^/]+/context/[^/]+"))
        ) {
            applicationAtHistory.compareAndSet(null, applicationAdmissions())
        }
        if (
            request.method == "PUT" &&
                request.requestURI.matches(encryptedSendRoute) &&
                !encryptedSendAllowed.get()
        ) {
            earlyEncryptedSends.updateAndGet { minOf(it + 1, 512) }
        }
        if (request.method == "PUT" && request.requestURI.matches(encryptedSendRoute)) {
            applicationBeforeSend.compareAndSet(
                -1,
                if (
                    applicationDeviceReads.get() >= 2 &&
                        applicationConversationReads.get() >= 2 &&
                        applicationRoomReads.get() >= 2 &&
                        applicationAdmissionFailures.get() == 0
                )
                    1
                else 0,
            )
            val room = UriUtils.decode(request.requestURI.split('/')[5], StandardCharsets.UTF_8)
            if (room == heldRoom.get()) heldRoomSends.updateAndGet { minOf(it + 1, 512) }
        }
        val keyRequest =
            if (request.requestURI == "/_matrix/client/v3/keys/upload")
                ContentCachingRequestWrapper(request, 65_536)
            else null
        try {
            if (
                request.method == "PUT" &&
                    request.requestURI.matches(encryptedSendRoute) &&
                    rejectNextEncrypted.compareAndSet(true, false)
            ) {
                rejectedEncryptedSends.incrementAndGet()
                response.status = 403
                response.contentType = "application/json"
                response.writer.write(
                    """{"errcode":"M_FORBIDDEN","error":"fixture send rejection"}"""
                )
                return
            }
            val cursor =
                if (request.method == "GET" && request.requestURI == "/_matrix/client/v3/sync")
                    request.getParameter("since")?.let {
                        MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    }
                else null
            val inject =
                synchronized(syncFaultLock) {
                    if (cursor != null && syncFaultArmed) {
                        syncFaultArmed = false
                        failedCursor = cursor
                        true
                    } else false
                }
            if (inject) {
                request.setAttribute("fixture.matrix.error", "collaboration_provider_busy")
                response.status = 429
                response.contentType = "application/json"
                response.setHeader("Cache-Control", "no-store")
                response.writer.write(
                    """{"errcode":"M_LIMIT_EXCEEDED","error":"Fixture provider busy","retry_after_ms":1000,"org.hris.error_code":"collaboration_provider_busy"}"""
                )
            } else {
                chain.doFilter(keyRequest ?: request, response)
                if (cursor != null && response.status == 200)
                    synchronized(syncFaultLock) {
                        if (failedCursor?.contentEquals(cursor) == true) cursorRetrySucceeded = true
                    }
            }
        } finally {
            recordApplicationAdmission(request, response)
            if (
                request.method == "PUT" &&
                    response.status == 200 &&
                    request.requestURI.matches(
                        Regex("/_matrix/client/v3/rooms/[^/]+/send/m.room.encrypted/[^/]+")
                    )
            )
                acceptedEncryptedSends.updateAndGet { minOf(it + 1, 1024) }
            if (request.requestURI.startsWith("/_matrix/"))
                synchronized(observations) {
                    if (observations.size < 512)
                        observations.add(
                            mapOf(
                                "method" to
                                    request.method
                                        .takeIf { it in setOf("GET", "POST", "PUT", "DELETE") }
                                        .orEmpty(),
                                "route" to
                                    request.requestURI.split('/').joinToString("/") {
                                        if (it in segments) it else "_"
                                    },
                                "query" to
                                    request.parameterMap.keys.filter { it in parameters }.sorted(),
                                "unknownQuery" to
                                    request.parameterMap.keys.any { it !in parameters },
                                "status" to response.status,
                                "contentLength" to request.contentLengthLong,
                                "transferEncoding" to
                                    (request.getHeader("Transfer-Encoding") != null),
                                "timeoutMillis" to
                                    (request.getParameter("timeout")?.takeIf {
                                        it.matches(Regex("[0-9]{1,9}"))
                                    } ?: "absent_or_invalid"),
                                "errorCode" to (request.getAttribute("fixture.matrix.error") ?: ""),
                            ) +
                                (keyRequest?.let {
                                    mapOf("keyShape" to nativeSdkKeyShape(it.contentAsByteArray))
                                } ?: emptyMap())
                        )
                }
        }
    }

    fun snapshot(): List<Map<String, Any>> = synchronized(observations) { observations.toList() }

    companion object {
        private val encryptedSendRoute =
            Regex("/_matrix/client/v3/rooms/[^/]+/send/m\\.room\\.encrypted/[^/]+")
        private val segments =
            setOf(
                "",
                "_matrix",
                "client",
                "v3",
                "r0",
                "unstable",
                "versions",
                "capabilities",
                "refresh",
                "sync",
                "keys",
                "upload",
                "query",
                "claim",
                "rooms",
                "receipt",
                "m.read",
                "m.read.private",
                "join",
                "state",
                "members",
                "messages",
                "context",
                "event",
                "send",
                "redact",
                "sendToDevice",
                "m.room.encrypted",
                "m.direct",
                "pushrules",
                "room_keys",
                "version",
                "user",
                "filter",
                "account_data",
                "profile",
                "displayname",
                "avatar_url",
                "whoami",
                "account",
                "turnServer",
                "voip",
            )
        private val parameters =
            setOf(
                "since",
                "timeout",
                "full_state",
                "set_presence",
                "use_state_after",
                "filter",
                "at",
                "membership",
                "not_membership",
                "from",
                "to",
                "dir",
                "limit",
            )
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NativeSdkGatewayTraceConfiguration {
    @Bean fun nativeSdkGatewayTrace() = NativeSdkGatewayTrace()

    @Bean fun nativeSdkErrorTrace() = NativeSdkErrorTrace()
}
