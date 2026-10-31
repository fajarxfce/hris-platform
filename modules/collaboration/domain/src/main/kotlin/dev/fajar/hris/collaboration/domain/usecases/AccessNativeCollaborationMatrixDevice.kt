package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** A fixed provider operation; all network I/O occurs between scoped admission transactions. */
class AccessNativeCollaborationMatrixDevice(
    private val gateway: CollaborationGatewaySessionRepository,
    private val registry: CollaborationRegistryRepository,
    private val credentials: CollaborationProviderSessionRepository,
    private val matrix: CollaborationMatrixRepository,
    private val keys: CollaborationDeviceKeyRepository,
    private val providerKeys: CollaborationMatrixKeyRepository,
    private val recipients: CollaborationRecipientRepository,
    private val conversations: CollaborationDirectRepository,
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun queryKeys(
        token: String,
        correlationId: UUID,
        request: CollaborationMatrixKeyQuery,
    ): Result<CollaborationMatrixKeyQueryResult> {
        val captured = snapshotCollaborationKeyQuery(request)
        if (captured is Result.Failed) return captured
        val query = (captured as Result.Success).value
        return recipientOperation(token, correlationId, query) { exchange, prepared ->
            val expanded = prepared.providerRequest()
            if (expanded.devices.isEmpty()) {
                exchange.confirm().map {
                    CollaborationMatrixKeyQueryResult(prepared.accounts.mapValues { emptyMap() })
                }
            } else {
                exchange.exchange { bearer, deadline ->
                    providerKeys.deviceKeys(expanded, bearer, deadline).flatMap {
                        verifyQuery(prepared, it)
                    }
                }
            }
        }
    }

    fun claimKeys(
        token: String,
        correlationId: UUID,
        request: CollaborationMatrixKeyClaim,
    ): Result<CollaborationMatrixKeyClaimResult> {
        val captured = snapshotCollaborationKeyClaim(request)
        if (captured is Result.Failed) return captured
        val claim = (captured as Result.Success).value
        val query =
            CollaborationMatrixKeyQuery(claim.oneTimeKeys.mapValues { it.value.keys.toList() })
        return recipientOperation(token, correlationId, query) { exchange, prepared ->
            val expanded = prepared.providerRequest()
            if (expanded.devices.isEmpty())
                return@recipientOperation exchange.confirm().map {
                    CollaborationMatrixKeyClaimResult(prepared.accounts.mapValues { emptyMap() })
                }
            // Verify pinned identity before consuming keys. Every provider exchange separately
            // rechecks current caller authority and the selected recipient set.
            val identities =
                exchange.exchange { bearer, deadline ->
                    providerKeys.deviceKeys(expanded, bearer, deadline).flatMap {
                        verifyQuery(prepared, it)
                    }
                }
            if (identities is Result.Failed) return@recipientOperation identities
            val selected =
                CollaborationMatrixKeyClaim(
                    expanded.devices.mapValues { (_, ids) ->
                        ids.associateWith { "signed_curve25519" }
                    }
                )
            exchange.exchange { bearer, deadline ->
                providerKeys.claimKeys(selected, bearer, deadline).flatMap {
                    verifyClaim(prepared, (identities as Result.Success).value, it)
                }
            }
        }
    }

    private fun verifyClaim(
        prepared: QueryPreparation,
        identities: CollaborationMatrixKeyQueryResult,
        response: CollaborationMatrixKeyClaimResult,
    ): Result<CollaborationMatrixKeyClaimResult> {
        val expected = prepared.providerRequest().devices
        if (response.oneTimeKeys.keys.any { it !in expected }) return invalidProvider()
        val output = linkedMapOf<String, Map<String, Map<String, CollaborationMatrixSignedKey>>>()
        for (user in prepared.accounts.keys) {
            val devices = response.oneTimeKeys[user] ?: emptyMap()
            if (devices.keys.any { it !in expected[user].orEmpty() }) return invalidProvider()
            for ((device, keys) in devices) {
                if (keys.size > 1) return invalidProvider()
                if (keys.isEmpty()) continue
                val identity = identities.deviceKeys[user]?.get(device) ?: return invalidProvider()
                val upload =
                    if (keys.values.single().fallback == true)
                        CollaborationMatrixKeyUpload(fallbackKeys = keys)
                    else CollaborationMatrixKeyUpload(oneTimeKeys = keys)
                if (validateCollaborationSignedKeys(upload, identity) is Result.Failed)
                    return invalidProvider()
            }
            output[user] =
                devices.mapValues { (_, keys) ->
                    keys.mapValues { (_, key) ->
                        key.copy(signatures = key.signatures.mapValues { it.value.toMap() })
                    }
                }
        }
        return Result.Success(CollaborationMatrixKeyClaimResult(output))
    }

    private fun <T> recipientOperation(
        token: String,
        correlationId: UUID,
        query: CollaborationMatrixKeyQuery,
        operation: (Exchange, QueryPreparation) -> Result<T>,
    ): Result<T> =
        admitted(
            token,
            correlationId,
            prepare = { initial, _ -> prepareQuery(initial, query) },
            extraAccounts = { initial ->
                collaborationKeyQueryAccounts(query, initial.companyId, provider.serverName).map {
                    it.values.toSet()
                }
            },
            revalidate = { initial, original ->
                prepareQuery(initial, query).flatMap { current ->
                    if (original != current) recipientsChanged() else Result.Success(Unit)
                }
            },
            operation = operation,
        )

    private fun prepareQuery(
        initial: CollaborationGatewaySession,
        request: CollaborationMatrixKeyQuery,
    ): Result<QueryPreparation> {
        val resolved =
            collaborationKeyQueryAccounts(request, initial.companyId, provider.serverName)
        if (resolved is Result.Failed) return resolved
        val accounts = (resolved as Result.Success).value
        val admittedAccounts = mutableSetOf(initial.accountId)
        for (account in accounts.values) {
            if (account == initial.accountId) continue
            val read = identities.access(account, initial.companyId)
            if (read is Result.Failed) return read
            val peer = (read as Result.Success).value
            if (
                peer == null ||
                    peer.account.id != account ||
                    !peer.account.active ||
                    !peer.companyActive ||
                    !peer.membershipActive ||
                    COLLABORATION_CHAT_PERMISSION !in peer.permissions
            )
                continue
            val pair = listOf(initial.accountId, account).sortedBy { it.toString() }
            val conversation = conversations.findPair(initial.companyId, pair[0], pair[1])
            if (conversation is Result.Failed) return conversation
            val direct = (conversation as Result.Success).value
            if (direct == null) continue
            if (
                direct.companyId != initial.companyId ||
                    direct.firstAccountId != pair[0] ||
                    direct.secondAccountId != pair[1]
            )
                return invalidProvider()
            admittedAccounts.add(account)
        }
        // Matrix clients may batch a removed/unknown peer with current recipients.
        // Empty entries reveal no key material and must not stall unrelated conversations.
        val requestedAccounts = accounts.values.filter { it in admittedAccounts }.toSet()
        if (requestedAccounts.isEmpty())
            return Result.Success(QueryPreparation(accounts, emptyMap()))
        val at = clock.instant()
        val read = recipients.devices(initial.companyId, requestedAccounts, at)
        if (read is Result.Failed) return read
        val eligible = (read as Result.Success).value
        if (
            eligible.size > 500 ||
                eligible.any {
                    it.companyId != initial.companyId ||
                        it.accountId !in requestedAccounts ||
                        it.receiveUntil <= at
                } ||
                eligible.groupingBy { it.accountId }.eachCount().values.any { it > 5 } ||
                eligible.map { it.deviceId }.toSet().size != eligible.size
        )
            return recipientsChanged()
        val names = accounts.entries.associate { it.value to it.key }
        val selected =
            eligible
                .filter { row ->
                    val requested = request.devices.getValue(names.getValue(row.accountId))
                    requested.isEmpty() || collaborationMatrixDeviceId(row.deviceId) in requested
                }
                .associateBy { it.deviceId }
        return Result.Success(QueryPreparation(accounts, selected))
    }

    private fun verifyQuery(
        prepared: QueryPreparation,
        response: CollaborationMatrixKeyQueryResult,
    ): Result<CollaborationMatrixKeyQueryResult> {
        val expected = prepared.providerRequest().devices
        if (response.deviceKeys.keys != expected.keys) return invalidProvider()
        val output = linkedMapOf<String, Map<String, CollaborationMatrixDeviceKeys>>()
        for ((user, account) in prepared.accounts) {
            val rows = prepared.devices.values.filter { it.accountId == account }
            val devices = response.deviceKeys[user] ?: emptyMap()
            if (devices.keys != rows.map { collaborationMatrixDeviceId(it.deviceId) }.toSet())
                return invalidProvider()
            for (row in rows) {
                val deviceId = collaborationMatrixDeviceId(row.deviceId)
                val checked =
                    validateCollaborationDeviceKeys(devices.getValue(deviceId), user, deviceId)
                if (checked is Result.Failed) return invalidProvider()
                if ((checked as Result.Success).value != row.fingerprint) return keysChanged()
            }
            output[user] = devices.toMap()
        }
        return Result.Success(CollaborationMatrixKeyQueryResult(output))
    }

    private data class QueryPreparation(
        val accounts: Map<String, UUID>,
        val devices: Map<UUID, CollaborationRecipientDevice>,
    ) {
        fun providerRequest() =
            CollaborationMatrixKeyQuery(
                accounts
                    .mapValues { (_, account) ->
                        devices.values
                            .filter { it.accountId == account }
                            .map { collaborationMatrixDeviceId(it.deviceId) }
                    }
                    .filterValues { it.isNotEmpty() }
            )
    }

    private fun recipientsChanged() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_recipients_changed"))

    fun identity(token: String, correlationId: UUID): Result<CollaborationMatrixIdentity> =
        admitted(token, correlationId, { _, _ -> Result.Success(Unit) }) { exchange, _ ->
            exchange
                .exchange { bearer, deadline -> matrix.whoAmI(bearer, deadline) }
                .flatMap { identity ->
                    if (
                        identity.userId != expectedUser(exchange.initial) ||
                            identity.deviceId !=
                                collaborationMatrixDeviceId(exchange.initial.deviceId) ||
                            identity.isGuest
                    )
                        invalidProvider()
                    else Result.Success(identity)
                }
        }

    fun uploadKeys(
        token: String,
        correlationId: UUID,
        request: CollaborationMatrixKeyUpload,
    ): Result<CollaborationMatrixKeyCounts> {
        val captured = snapshotCollaborationKeyUpload(request)
        if (captured is Result.Failed) return captured
        val upload = (captured as Result.Success).value
        return admitted(
            token,
            correlationId,
            { initial, observation -> reserveKeys(initial, observation, upload) },
        ) { exchange, reservation ->
            // The SDK may query counts before generating its first identity. This writes no keys
            // and must not create a publication or turn a pending reservation into a verified one.
            if (upload.deviceKeys == null && !upload.hasSignedKeys)
                return@admitted exchange.exchange { bearer, deadline ->
                    providerKeys.upload(upload, bearer, deadline)
                }
            val retained = reservation ?: return@admitted keysRequired()
            val existing = exchange.readKeys()
            if (existing is Result.Failed) return@admitted existing
            val prior = (existing as Result.Success).value
            if (prior != null) {
                val validated = validatePublicKeys(exchange.initial, retained, prior)
                if (validated is Result.Failed) return@admitted validated
            }
            val identity = upload.deviceKeys ?: prior ?: return@admitted keysRequired()
            val proof = validateCollaborationSignedKeys(upload, identity)
            if (proof is Result.Failed) return@admitted proof
            val response =
                exchange.exchange { bearer, deadline ->
                    providerKeys.upload(upload, bearer, deadline)
                }
            if (response is Result.Failed) return@admitted response
            val readback = exchange.readKeys()
            if (readback is Result.Failed) return@admitted readback
            val published = (readback as Result.Success).value ?: return@admitted invalidProvider()
            validatePublicKeys(exchange.initial, retained, published).flatMap {
                exchange.publishKeys(retained).flatMap { response }
            }
        }
    }

    private fun reserveKeys(
        initial: CollaborationGatewaySession,
        observation: Observation,
        upload: CollaborationMatrixKeyUpload,
    ): Result<CollaborationDeviceKeyPublication?> {
        val fingerprint =
            upload.deviceKeys?.let {
                validateCollaborationDeviceKeys(
                    it,
                    expectedUser(initial),
                    collaborationMatrixDeviceId(initial.deviceId),
                )
            }
        if (fingerprint is Result.Failed) return fingerprint
        val locked = keys.lock(initial.companyId, initial.deviceId)
        if (locked is Result.Failed) return locked
        val found = keys.find(initial.companyId, initial.accountId, initial.deviceId)
        if (found is Result.Failed) return found
        val existing = (found as Result.Success).value
        if (existing != null) {
            if (
                fingerprint != null && (fingerprint as Result.Success).value != existing.fingerprint
            )
                return keysChanged()
            if (existing.version == 0L && upload.deviceKeys == null && upload.hasSignedKeys)
                return keysRequired()
            return Result.Success(existing)
        }
        if (fingerprint == null)
            return if (upload.hasSignedKeys) keysRequired() else Result.Success(null)
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val candidate =
            CollaborationDeviceKeyPublication(
                initial.companyId,
                initial.accountId,
                initial.deviceId,
                (fingerprint as Result.Success).value,
                0,
                now,
                null,
            )
        return keys
            .reserve(candidate)
            .flatMap {
                journal.record(
                    observation.actor,
                    ChangeRecord(
                        "collaboration_device",
                        initial.deviceId,
                        "collaboration.device_keys_reserved",
                        mapOf("version" to "0"),
                    ),
                )
            }
            .map { candidate }
    }

    private fun validatePublicKeys(
        initial: CollaborationGatewaySession,
        retained: CollaborationDeviceKeyPublication,
        value: CollaborationMatrixDeviceKeys,
    ): Result<Unit> =
        when (
            val result =
                validateCollaborationDeviceKeys(
                    value,
                    expectedUser(initial),
                    collaborationMatrixDeviceId(initial.deviceId),
                )
        ) {
            is Result.Failed -> invalidProvider()
            is Result.Success ->
                if (result.value == retained.fingerprint) Result.Success(Unit) else keysChanged()
        }

    // Only the fixed public operations above can use this private runner. Callers cannot
    // supply provider paths or callbacks. Preparation, admission and publication remain here.
    private fun <S, T> admitted(
        token: String,
        correlationId: UUID,
        prepare: (CollaborationGatewaySession, Observation) -> Result<S>,
        extraAccounts: (CollaborationGatewaySession) -> Result<Set<UUID>> = {
            Result.Success(emptySet())
        },
        revalidate: (CollaborationGatewaySession, S) -> Result<Unit> = { _, _ ->
            Result.Success(Unit)
        },
        operation: (Exchange, S) -> Result<T>,
    ): Result<T> {
        if (!service.enabled || !provider.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (!validCollaborationGatewayAccess(token))
            return collaborationGatewayAuthenticationRequired()
        val lookup = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, correlationId)
        val resolved = transactions.run(lookup) { gateway.findAccess(token) }
        if (resolved is Result.Failed) return resolved
        val initial =
            (resolved as Result.Success).value
                ?: return collaborationGatewayAuthenticationRequired()
        val resolvedAccounts = extraAccounts(initial)
        if (resolvedAccounts is Result.Failed) return resolvedAccounts
        val relatedAccounts = (resolvedAccounts as Result.Success).value.toSet()
        val scope = lookup.copy(accountId = initial.accountId, companyId = initial.companyId)
        val captured =
            transactions.run(scope) {
                observe(scope, initial, relatedAccounts).flatMap { observation ->
                    prepare(initial, observation).flatMap { extra ->
                        validateObservation(observation.admission, observation.admission).map {
                            Preparation(observation, extra)
                        }
                    }
                }
            }
        if (captured is Result.Failed) return captured
        val prepared = (captured as Result.Success).value
        val observed = prepared.observation
        val credential =
            if (usable(observed.credential)) Result.Success(requireNotNull(observed.credential))
            else renew(scope, initial, observed)
        if (credential is Result.Failed) return credential
        val active = (credential as Result.Success).value
        val deadline =
            minOf(
                observed.admission.expiresAt,
                initial.accessExpiresAt,
                requireNotNull(active.expiresAt),
            )
        if (clock.instant() >= deadline) return changed()
        return operation(
            Exchange(scope, initial, observed.admission, active, deadline, relatedAccounts) {
                revalidate(initial, prepared.extra)
            },
            prepared.extra,
        )
    }

    private inner class Exchange(
        val scope: Actor,
        val initial: CollaborationGatewaySession,
        val admission: CollaborationAdmission,
        val credential: CollaborationProviderSession,
        val deadline: Instant,
        val relatedAccounts: Set<UUID>,
        val extraCheck: () -> Result<Unit>,
    ) {
        fun <T> exchange(request: (String, Instant) -> Result<T>): Result<T> {
            val before = check()
            if (before is Result.Failed) return before
            val response = request(credential.accessToken, deadline)
            // Live denial also takes precedence over an unavailable/error provider response.
            val after = check()
            return if (after is Result.Failed) after else response
        }

        fun readKeys() = exchange { bearer, until ->
            providerKeys.ownDeviceKeys(
                expectedUser(initial),
                collaborationMatrixDeviceId(initial.deviceId),
                bearer,
                until,
            )
        }

        fun confirm(): Result<Unit> = check()

        private fun check(): Result<Unit> =
            transactions.run(scope) {
                observe(scope, initial, relatedAccounts).flatMap { current ->
                    validateObservation(admission, current.admission).flatMap {
                        extraCheck().flatMap {
                            if (clock.instant() >= deadline) changed() else Result.Success(Unit)
                        }
                    }
                }
            }

        fun publishKeys(retained: CollaborationDeviceKeyPublication): Result<Unit> =
            transactions.run(scope) {
                observe(scope, initial).flatMap observation@{ current ->
                    val validated = validateObservation(admission, current.admission)
                    if (validated is Result.Failed) return@observation validated
                    if (clock.instant() >= deadline) return@observation changed()
                    val locked = keys.lock(initial.companyId, initial.deviceId)
                    if (locked is Result.Failed) return@observation locked
                    val found = keys.find(initial.companyId, initial.accountId, initial.deviceId)
                    if (found is Result.Failed) return@observation found
                    val live = (found as Result.Success).value ?: return@observation keysChanged()
                    if (
                        live.fingerprint != retained.fingerprint ||
                            live.reservedAt != retained.reservedAt
                    )
                        return@observation keysChanged()
                    val publication =
                        if (live.version == 1L) Result.Success(Unit)
                        else
                            keys
                                .publish(live, clock.instant().truncatedTo(ChronoUnit.MICROS))
                                .flatMap {
                                    journal.record(
                                        current.actor,
                                        ChangeRecord(
                                            "collaboration_device",
                                            initial.deviceId,
                                            "collaboration.device_keys_published",
                                            mapOf("version" to "1"),
                                        ),
                                    )
                                }
                    publication
                        .flatMap { validateObservation(admission, current.admission) }
                        .flatMap {
                            if (clock.instant() >= deadline) changed() else Result.Success(Unit)
                        }
                }
            }
    }

    private class Preparation<T>(val observation: Observation, val extra: T)

    private fun keysChanged() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_device_key_recovery_required"))

    private fun keysRequired() =
        Result.Failed(Failure(FailureKind.VALIDATION, "collaboration_device_keys_required"))

    private fun renew(
        scope: Actor,
        initial: CollaborationGatewaySession,
        prepared: Observation,
    ): Result<CollaborationProviderSession> {
        val issued =
            matrix.login(
                CollaborationMatrixLogin(
                    initial.companyId,
                    initial.accountId,
                    initial.deviceId,
                    prepared.admission.admittedAt,
                    prepared.admission.expiresAt,
                )
            )
        // Losing/late candidates expire privately. Synapse logout would erase the winning keys.
        return transactions.run(scope) {
            observe(scope, initial).flatMap { current ->
                validateObservation(prepared.admission, current.admission).flatMap publication@{
                    if (issued is Result.Failed) return@publication issued
                    val candidate = (issued as Result.Success).value
                    if (
                        candidate.userId != expectedUser(initial) ||
                            candidate.deviceId != collaborationMatrixDeviceId(initial.deviceId)
                    )
                        return@publication invalidProvider()
                    val prior = current.credential
                    if (prior?.generation != prepared.credential?.generation)
                        return@publication if (usable(prior)) Result.Success(requireNotNull(prior))
                        else changed()
                    val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
                    if (
                        candidate.expiresAt <= now.plusSeconds(10) ||
                            candidate.expiresAt > now.plusSeconds(600)
                    )
                        return@publication invalidProvider()
                    if ((prior?.generation ?: -1) >= 999999)
                        return@publication Result.Failed(
                            Failure(
                                FailureKind.UNAUTHENTICATED,
                                "collaboration_provider_renewal_required",
                            )
                        )
                    val next =
                        CollaborationProviderSession(
                            initial.companyId,
                            initial.accountId,
                            initial.deviceId,
                            candidate.userId,
                            candidate.deviceId,
                            (prior?.generation ?: -1) + 1,
                            candidate.accessToken,
                            prior?.createdAt ?: now,
                            now,
                            candidate.expiresAt,
                        )
                    val stored =
                        if (prior == null) credentials.create(next)
                        else credentials.rotate(next, prior.generation)
                    stored
                        .flatMap {
                            journal.record(
                                current.actor,
                                ChangeRecord(
                                    "collaboration_device",
                                    initial.deviceId,
                                    "collaboration.provider_renewed",
                                    mapOf("generation" to next.generation.toString()),
                                ),
                            )
                        }
                        .flatMap { validateObservation(prepared.admission, current.admission) }
                        .flatMap {
                            if (initial.accessExpiresAt <= clock.instant() || !usable(next))
                                changed()
                            else Result.Success(next)
                        }
                }
            }
        }
    }

    /** The token lookup identifies locks only; this method reacquires every authority input. */
    private fun observe(
        scope: Actor,
        initial: CollaborationGatewaySession,
        relatedAccounts: Set<UUID> = emptySet(),
    ): Result<Observation> {
        val company = initial.companyId
        val pg = policies.lock(company, shared = true)
        if (pg is Result.Failed) return pg
        val cg = companies.lock(company, shared = true)
        if (cg is Result.Failed) return cg
        val mg = members.lock(company, shared = true)
        if (mg is Result.Failed) return mg
        for (account in (relatedAccounts + initial.accountId).sorted()) {
            val ag = identities.lockAccount(account, shared = true)
            if (ag is Result.Failed) return ag
        }
        val locked = sessions.lock(initial.nativeSessionId, initial.accountId)
        if (locked is Result.Failed) return locked
        val parent =
            (locked as Result.Success).value ?: return collaborationGatewayAuthenticationRequired()
        if (parent.id != initial.nativeSessionId || parent.accountId != initial.accountId)
            return collaborationGatewayAuthenticationRequired()
        val actor =
            scope.copy(
                permissions = setOf(COLLABORATION_CHAT_PERMISSION),
                authenticatedAt = parent.authenticatedAt,
                mfaVerifiedAt = parent.mfaVerifiedAt,
                credentialVersion = parent.credentialVersion,
            )
        val acquired = identities.access(initial.accountId, company)
        if (acquired is Result.Failed) return acquired
        val access = (acquired as Result.Success).value
        val bound = registry.findBound(company, initial.accountId, parent.id)
        if (bound is Result.Failed) return bound
        val device = (bound as Result.Success).value
        if (device == null || device.id != initial.deviceId || device.platform != initial.platform)
            return collaborationGatewayAuthenticationRequired()
        val read = gateway.find(company, initial.accountId, initial.deviceId, lock = true)
        if (read is Result.Failed) return read
        val head =
            (read as Result.Success).value ?: return collaborationGatewayAuthenticationRequired()
        if (head != initial || head.accessExpiresAt <= clock.instant())
            return collaborationGatewayAuthenticationRequired()
        val stored = credentials.find(company, initial.accountId, device.id)
        if (stored is Result.Failed) return stored
        val credential = (stored as Result.Success).value
        if (
            credential != null &&
                (credential.companyId != company ||
                    credential.accountId != initial.accountId ||
                    credential.deviceId != device.id ||
                    credential.matrixUserId != expectedUser(initial) ||
                    credential.matrixDeviceId != collaborationMatrixDeviceId(device.id))
        )
            return invalidProvider()
        val effective = policies.effective(company, clock.instant())
        if (effective is Result.Failed) return effective
        val at = clock.instant()
        val client =
            ClientVersion(
                if (head.platform == CollaborationPlatform.ANDROID) ClientPlatform.ANDROID
                else ClientPlatform.IOS,
                head.clientBuild,
            )
        return nativeCollaborationClientDeadline((effective as Result.Success).value, client, at)
            .flatMap { deadline ->
                admitCollaborationDevice(
                        actor,
                        access,
                        parent,
                        parent.version,
                        device,
                        at,
                        security,
                    )
                    .flatMap {
                        val admission =
                            it.copy(expiresAt = minOf(it.expiresAt, deadline, head.accessExpiresAt))
                        validateCollaborationGatewaySession(head, admission, device.platform, at)
                            .map { Observation(actor, admission, credential) }
                    }
            }
    }

    // Live denial is returned by observe. A stale captured observation only asks for reacquisition;
    // it must not look like permanent permission removal after an ordinary native refresh.
    private fun validateObservation(
        prepared: CollaborationAdmission,
        current: CollaborationAdmission,
    ): Result<Unit> =
        if (validateCollaborationExchange(prepared, current, clock.instant()) is Result.Failed)
            changed()
        else Result.Success(Unit)

    private fun usable(credential: CollaborationProviderSession?): Boolean =
        credential != null &&
            collaborationProviderCredentialIsCurrent(credential, clock.instant()) &&
            requireNotNull(credential.expiresAt) > clock.instant().plusSeconds(10)

    private fun expectedUser(session: CollaborationGatewaySession) =
        "@${collaborationMatrixLocalpart(session.companyId, session.accountId)}:${provider.serverName}"

    private fun invalidProvider() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response"))

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))

    private class Observation(
        val actor: Actor,
        val admission: CollaborationAdmission,
        val credential: CollaborationProviderSession?,
    )
}
