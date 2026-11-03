package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixCredential
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceMatrixLogin
import dev.fajar.hris.core.domain.Result

/**
 * Internal I/O outside transactions. Caller must reauthorize and fence candidate publication.
 * Losing candidates expire; Matrix logout would delete the retained device and its E2EE keys.
 */
interface CollaborationSpaceMatrixRepository {
    fun login(request: CollaborationSpaceMatrixLogin): Result<CollaborationMatrixCredential>
}
