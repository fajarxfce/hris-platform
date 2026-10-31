package dev.fajar.hris.collaboration.data.crypto

import java.util.UUID

/** Keep recovery_set.py's schema-106 projection in sync with this persisted AAD contract. */
fun collaborationProviderPurpose(
    company: UUID,
    account: UUID,
    device: UUID,
    generation: Long,
): String = "hris:matrix:$company:$account:$device:$generation"
