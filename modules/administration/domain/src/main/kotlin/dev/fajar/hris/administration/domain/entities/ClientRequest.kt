package dev.fajar.hris.administration.domain.entities

/**
 * The adapter derives session transport from authentication; build metadata remains self-reported.
 */
data class ClientRequest(val version: ClientVersion?, val native: Boolean)
