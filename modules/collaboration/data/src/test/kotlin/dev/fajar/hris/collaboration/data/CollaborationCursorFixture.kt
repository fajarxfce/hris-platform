package dev.fajar.hris.collaboration.data

import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import java.util.Base64

/** Public synthetic key, shared only by isolated test owners and their protocol adapters. */
fun collaborationCursorFixture() =
    JceIdentityTokenDataSource(
        parseIdentityKeyring(
            "fixture",
            "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 17 }),
        )
    )
