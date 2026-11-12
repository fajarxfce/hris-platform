package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.TimeUnit
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter

/** Isolated test gateway counters only; never retain credentials, paths, IDs or request bodies. */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class NativeSpaceRecoveryTrace : OncePerRequestFilter() {
    private val counts = mutableMapOf<String, Int>()
    private val kinds = mutableMapOf<String, Int>()
    private var active = false
    private var syncObservedAt: Long? = null
    private var syncOverflowed = false
    private val syncTimings = mutableListOf<NativeSyncTiming>()

    @Synchronized
    fun arm() {
        check(!active)
        active = true
    }

    @Synchronized fun snapshot(): Map<String, Int> = counts.toMap()

    @Synchronized fun requestKinds(): Map<String, Int> = kinds.toMap()

    /**
     * Optional finite idle observation; no cursor, identity, URL or request content is retained.
     */
    @Synchronized
    fun startSyncObservation() {
        check(syncObservedAt == null && syncTimings.isEmpty())
        syncObservedAt = System.nanoTime()
    }

    @Synchronized
    fun finishSyncObservation(): NativeSyncObservation {
        check(syncObservedAt != null)
        syncObservedAt = null
        return NativeSyncObservation(syncTimings.toList(), syncOverflowed)
    }

    @Synchronized
    private fun beginSync(request: HttpServletRequest): Pair<Int, Long>? {
        val observedAt = syncObservedAt ?: return null
        if (syncTimings.size == 8) {
            syncOverflowed = true
            return null
        }
        val started = System.nanoTime()
        syncTimings.add(
            NativeSyncTiming(
                startedMillis = TimeUnit.NANOSECONDS.toMillis(started - observedAt),
                timeoutMillis = request.getParameter("timeout")?.toIntOrNull(),
                sincePresent = !request.getParameter("since").isNullOrEmpty(),
                fullState = request.getParameter("full_state") == "true",
            )
        )
        return syncTimings.lastIndex to started
    }

    @Synchronized
    private fun completeSync(start: Pair<Int, Long>, status: Int) {
        if (syncObservedAt == null) return
        syncTimings[start.first] =
            syncTimings[start.first].copy(
                elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start.second),
                status = status,
            )
    }

    @Synchronized
    private fun recordKind(name: String) {
        if (active) kinds[name] = minOf(10_000, kinds.getOrDefault(name, 0) + 1)
    }

    @Synchronized
    private fun record(name: String) {
        if (active) counts[name] = minOf(10_000, counts.getOrDefault(name, 0) + 1)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (!request.requestURI.startsWith("/_matrix/")) {
            chain.doFilter(request, response)
            return
        }
        record("requests")
        val path = request.requestURI
        recordKind(
            when {
                path.endsWith("/sync") -> "sync"
                path.endsWith("/messages") -> "history"
                path.contains("/event/") -> "eventRead"
                path.endsWith("/keys/query") -> "keysQuery"
                path.endsWith("/keys/upload") -> "keysUpload"
                path.endsWith("/keys/claim") -> "keysClaim"
                path.contains("/send/m.room.encrypted/") -> "encryptedSend"
                path.contains("/account_data/") && request.method == "GET" -> "accountDataRead"
                path.contains("/account_data/") && request.method == "PUT" -> "accountDataWrite"
                path.endsWith("/state") || path.contains("/state/") -> "roomState"
                path.endsWith("/joined_members") -> "joinedMembers"
                path.endsWith("/versions") -> "versions"
                path.endsWith("/capabilities") -> "capabilities"
                path.endsWith("/pushrules/") -> "pushRules"
                path.contains("/filter") -> "filter"
                request.method == "GET" -> "otherGet"
                request.method == "POST" -> "otherPost"
                request.method == "PUT" -> "otherPut"
                else -> "other"
            }
        )
        when {
            request.requestURI.endsWith("/sync") -> record("sync")
            request.requestURI.endsWith("/messages") &&
                request.getParameter("dir") == "b" &&
                request.getParameter("limit") == "20" -> record("providerPage")
            request.requestURI == "/_matrix/client/unstable/dev.fajar.hris/pusher" ->
                record("managedPusher")
            request.requestURI.contains("/account_data/m.secret_storage.") ->
                record("secretStorage")
            request.requestURI.contains("/send/m.room.encrypted/") -> record("encryptedSend")
        }
        val sync = if (path.endsWith("/sync")) beginSync(request) else null
        try {
            chain.doFilter(request, response)
        } finally {
            if (sync != null) completeSync(sync, response.status)
        }
        when {
            response.status == 401 || response.status == 403 -> record("rejected")
            response.status >= 500 -> record("serverError")
        }
    }
}

data class NativeSyncTiming(
    val startedMillis: Long,
    val timeoutMillis: Int?,
    val sincePresent: Boolean,
    val fullState: Boolean,
    val elapsedMillis: Long? = null,
    val status: Int? = null,
)

data class NativeSyncObservation(val requests: List<NativeSyncTiming>, val overflowed: Boolean)

@TestConfiguration(proxyBeanMethods = false)
class NativeSpaceRecoveryTraceConfiguration {
    @Bean fun spaceRecoveryTrace() = NativeSpaceRecoveryTrace()
}
