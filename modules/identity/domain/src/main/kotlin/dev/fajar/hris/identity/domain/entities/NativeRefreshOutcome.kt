package dev.fajar.hris.identity.domain.entities

sealed interface NativeRefreshOutcome {
    data class Accepted(val tokens: NativeTokens) : NativeRefreshOutcome

    data class Rejected(val failure: dev.fajar.hris.core.domain.Failure) : NativeRefreshOutcome
}
