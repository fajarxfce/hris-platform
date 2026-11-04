package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

/** A detached observation of the scoped recipient projection, never an independent access grant. */
data class CollaborationSpaceKeySelection(
    val accounts: Map<String, UUID>,
    val devices: Map<UUID, CollaborationSpaceRecipientDevice>,
) {
    fun providerRequest() =
        CollaborationMatrixKeyQuery(
            accounts
                .mapValues { (user, account) ->
                    devices.values
                        .filter { it.accountId == account && it.matrixUserId == user }
                        .map { it.matrixDeviceId }
                }
                .filterValues { it.isNotEmpty() }
        )

    override fun toString() = "CollaborationSpaceKeySelection(<metadata>)"
}

/**
 * Only current, already scoped rows may be supplied. Exact aliases never substitute for each other.
 */
fun selectCollaborationSpaceKeys(
    spaceId: UUID,
    serverName: String,
    request: CollaborationMatrixKeyQuery,
    eligible: List<CollaborationSpaceRecipientDevice>,
    at: Instant,
    includeReserved: Boolean,
): Result<CollaborationSpaceKeySelection> {
    val parsed = collaborationSpaceKeyQueryAccounts(request, spaceId, serverName)
    if (parsed is Result.Failed) return parsed
    val accounts = (parsed as Result.Success).value
    if (
        eligible.size > 500 ||
            eligible.any {
                !validCollaborationSpaceRecipient(it) ||
                    it.spaceId != spaceId ||
                    it.serverName != serverName ||
                    it.accountId !in accounts.values ||
                    it.receiveUntil <= at
            } ||
            eligible.groupingBy { it.accountId }.eachCount().values.any { it > 5 } ||
            eligible.map { it.deviceId }.toSet().size != eligible.size ||
            eligible.map { it.matrixUserId to it.matrixDeviceId }.toSet().size != eligible.size
    )
        return collaborationSpaceRecipientsChanged()
    val selected =
        eligible
            .filter { row ->
                val devices = request.devices[row.matrixUserId]
                devices != null &&
                    accounts[row.matrixUserId] == row.accountId &&
                    (includeReserved || row.publicationConfirmed) &&
                    (devices.isEmpty() || row.matrixDeviceId in devices)
            }
            .associateBy { it.deviceId }
    return Result.Success(CollaborationSpaceKeySelection(accounts, selected))
}

/**
 * Detach all nested identity collections before checking signatures or returning provider results.
 */
fun verifyCollaborationSpaceKeyQuery(
    selection: CollaborationSpaceKeySelection,
    response: CollaborationMatrixKeyQueryResult,
): Result<CollaborationMatrixKeyQueryResult> {
    val expected = selection.providerRequest().devices
    if (response.deviceKeys.size > 100 || response.deviceKeys.keys != expected.keys)
        return invalidSpaceKeyResponse()
    val output = linkedMapOf<String, Map<String, CollaborationMatrixDeviceKeys>>()
    for (user in selection.accounts.keys) {
        val rows =
            selection.devices.values
                .filter { it.matrixUserId == user }
                .associateBy { it.matrixDeviceId }
        val observed = response.deviceKeys[user] ?: emptyMap()
        if (
            observed.size > 5 ||
                observed.keys.any { it !in rows } ||
                rows.any { (id, row) -> row.publicationConfirmed && id !in observed }
        )
            return invalidSpaceKeyResponse()
        val detached = linkedMapOf<String, CollaborationMatrixDeviceKeys>()
        for ((id, device) in observed) {
            val captured =
                snapshotCollaborationKeyUpload(CollaborationMatrixKeyUpload(deviceKeys = device))
            if (captured is Result.Failed) return invalidSpaceKeyResponse()
            detached[id] = requireNotNull((captured as Result.Success).value.deviceKeys)
        }
        for ((id, device) in detached) {
            val proof = validateCollaborationDeviceKeys(device, user, id)
            if (proof is Result.Failed) return invalidSpaceKeyResponse()
            if ((proof as Result.Success).value != rows.getValue(id).fingerprint)
                return Result.Failed(
                    Failure(FailureKind.CONFLICT, "collaboration_space_device_keys_changed")
                )
        }
        output[user] = detached
    }
    return Result.Success(CollaborationMatrixKeyQueryResult(output))
}

/**
 * Only verified pinned identities may authorize consumption of a signed one-time or fallback key.
 */
fun verifyCollaborationSpaceKeyClaim(
    selection: CollaborationSpaceKeySelection,
    identities: CollaborationMatrixKeyQueryResult,
    response: CollaborationMatrixKeyClaimResult,
): Result<CollaborationMatrixKeyClaimResult> {
    val expected = selection.providerRequest().devices
    if (response.oneTimeKeys.size > 100 || response.oneTimeKeys.keys.any { it !in expected })
        return invalidSpaceKeyResponse()
    val output = linkedMapOf<String, Map<String, Map<String, CollaborationMatrixSignedKey>>>()
    for (user in selection.accounts.keys) {
        val devices = response.oneTimeKeys[user] ?: emptyMap()
        if (devices.size > 5 || devices.keys.any { it !in expected[user].orEmpty() })
            return invalidSpaceKeyResponse()
        val captured = linkedMapOf<String, Map<String, CollaborationMatrixSignedKey>>()
        for ((device, keys) in devices) {
            if (keys.size > 1) return invalidSpaceKeyResponse()
            if (keys.isEmpty()) {
                captured[device] = emptyMap()
                continue
            }
            val identity =
                identities.deviceKeys[user]?.get(device) ?: return invalidSpaceKeyResponse()
            val value =
                if (keys.values.single().fallback == true)
                    CollaborationMatrixKeyUpload(fallbackKeys = keys)
                else CollaborationMatrixKeyUpload(oneTimeKeys = keys)
            val snapshot = snapshotCollaborationKeyUpload(value)
            if (snapshot is Result.Failed) return invalidSpaceKeyResponse()
            val upload = (snapshot as Result.Success).value
            if (validateCollaborationSignedKeys(upload, identity) is Result.Failed)
                return invalidSpaceKeyResponse()
            captured[device] =
                if (upload.fallbackKeys.isNotEmpty()) upload.fallbackKeys else upload.oneTimeKeys
        }
        output[user] = captured
    }
    return Result.Success(CollaborationMatrixKeyClaimResult(output))
}

fun collaborationSpaceRecipientsChanged() =
    Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_recipients_changed"))

private fun invalidSpaceKeyResponse() =
    Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response"))
