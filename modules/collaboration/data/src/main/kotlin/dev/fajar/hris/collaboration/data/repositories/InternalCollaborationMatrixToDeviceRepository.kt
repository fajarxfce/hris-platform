package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationToDevice
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixToDeviceRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant

class InternalCollaborationMatrixToDeviceRepository(private val source: MatrixToDeviceDataSource) :
    CollaborationMatrixToDeviceRepository {
    override fun send(
        request: CollaborationToDevice,
        token: String,
        deadline: Instant,
    ): Result<Unit> = safeMatrixProviderCall {
        require(request.transactionId.length in 1..128 && request.messages.size in 1..100)
        var characters = 0
        val messages =
            request.messages.mapValues { (user, devices) ->
                require(user.length in 1..255 && devices.size in 1..5)
                devices.mapValues { (device, envelope) ->
                    characters += envelope.body.length
                    require(
                        device.length in 1..128 &&
                            envelope.body.length in 1..65536 &&
                            characters <= 524288 &&
                            envelope.senderKey.length in 43..44 &&
                            envelope.recipientKey.length in 43..44 &&
                            envelope.messageType in 0..1 &&
                            (envelope.messageId?.length ?: 1) in 1..128
                    )
                    buildMap<String, Any> {
                        put("algorithm", "m.olm.v1.curve25519-aes-sha2")
                        put("sender_key", envelope.senderKey)
                        put(
                            "ciphertext",
                            mapOf(
                                envelope.recipientKey to
                                    mapOf("body" to envelope.body, "type" to envelope.messageType)
                            ),
                        )
                        envelope.messageId?.let { put("org.matrix.msgid", it) }
                    }
                }
            }
        val response =
            source.sendEncrypted(
                request.transactionId,
                mapOf("messages" to messages),
                token,
                deadline,
            )
        if (!response.isObject || !response.isEmpty) throw MatrixResponseException()
    }
}
