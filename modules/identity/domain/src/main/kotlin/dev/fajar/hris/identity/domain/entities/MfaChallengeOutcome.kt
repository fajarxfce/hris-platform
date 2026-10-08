package dev.fajar.hris.identity.domain.entities

import dev.fajar.hris.core.domain.Failure

/** Denials can commit attempt accounting before conversion into an API failure. */
sealed interface MfaChallengeOutcome {
    data class Accepted(val result: MfaChallengeSuccess) : MfaChallengeOutcome

    data class Denied(val failure: Failure) : MfaChallengeOutcome
}
