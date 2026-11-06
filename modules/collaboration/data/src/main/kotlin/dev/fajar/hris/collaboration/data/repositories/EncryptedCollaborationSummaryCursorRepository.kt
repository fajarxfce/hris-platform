package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSummaryCursorRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.time.Instant
import java.util.UUID

class EncryptedCollaborationSummaryCursorRepository(private val crypto: IdentityTokenDataSource) :
    CollaborationSummaryCursorRepository {
    override fun seal(cursor: CollaborationSummaryCursor): Result<String> =
        safeCollaborationSyncCursorCall {
            validate(cursor)
            crypto.encrypt(
                purpose(cursor.scope.owner),
                listOf(
                        cursor.scope.credentialVersion,
                        cursor.scope.companyVersion,
                        cursor.scope.membershipVersion,
                        cursor.epoch,
                        cursor.phase.name,
                        cursor.position,
                        cursor.issuedAt.epochSecond,
                        cursor.expiresAt.epochSecond,
                        cursor.upperPosition ?: "",
                        cursor.after ?: "",
                        cursor.page,
                    )
                    .joinToString("\n"),
            )
        }

    override fun open(
        owner: CollaborationSummaryOwner,
        token: String,
    ): Result<CollaborationSummaryCursor> = safeCollaborationSyncCursorCall {
        require(token.length in 1..2048 && token.all { it.code in 33..126 })
        val fields = crypto.decrypt(purpose(owner), token).split('\n')
        require(fields.size == 11)
        CollaborationSummaryCursor(
                CollaborationSummaryScope(
                    owner,
                    fields[0].toLong(),
                    fields[1].toLong(),
                    fields[2].toLong(),
                ),
                uuid(fields[3]),
                CollaborationSummaryPhase.valueOf(fields[4]),
                fields[5].toLong(),
                instant(fields[6]),
                instant(fields[7]),
                fields[8].takeIf { it.isNotEmpty() }?.toLong(),
                fields[9].takeIf { it.isNotEmpty() }?.let(::uuid),
                fields[10].toInt(),
            )
            .also(::validate)
    }

    private fun purpose(owner: CollaborationSummaryOwner) =
        "collaboration.summary.v1:" +
            crypto.hash("${owner.companyId}\n${owner.accountId}\n${owner.parentId}")

    private fun uuid(value: String) =
        UUID.fromString(value).also { require(it.toString() == value) }

    private fun instant(value: String) =
        value.toLong().also { require(it in 0..253402300799L) }.let(Instant::ofEpochSecond)

    private fun validate(cursor: CollaborationSummaryCursor) {
        require(
            cursor.scope.credentialVersion >= 0 &&
                cursor.scope.companyVersion >= 0 &&
                cursor.scope.membershipVersion >= 0
        )
        require(
            cursor.position >= 0 &&
                cursor.upperPosition?.let { it >= cursor.position } != false &&
                cursor.page in 0..999
        )
        require(
            listOf(cursor.issuedAt, cursor.expiresAt).all {
                it.nano == 0 && it.epochSecond in 0..253402300799L
            }
        )
        require(cursor.expiresAt > cursor.issuedAt)
        require(
            if (cursor.phase == CollaborationSummaryPhase.BOOTSTRAP) cursor.upperPosition == null
            else cursor.after == null && cursor.page == 0
        )
    }
}
