package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result

/**
 * Internal provider I/O, outside SQL transactions. No retry or public credential publication. A
 * successful login may arrive after HRIS authority changes: the caller must reauthorize and compare
 * the expected provider identity before storing/activating it. A losing candidate is never
 * published and expires within ten minutes behind the private provider network. Synapse logout
 * deletes ALL tokens and keys for the device, even when called with an expired bearer.
 */
interface CollaborationMatrixRepository {
    fun login(request: CollaborationMatrixLogin): Result<CollaborationMatrixCredential>

    /** Only after irreversible HRIS device revocation. Deletes the provider device and its keys. */
    fun logoutDevice(accessToken: String): Result<Unit>
}
