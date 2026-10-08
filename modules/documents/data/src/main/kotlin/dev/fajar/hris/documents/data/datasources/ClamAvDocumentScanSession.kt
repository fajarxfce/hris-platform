package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.errors.DocumentScannerProtocolException
import dev.fajar.hris.documents.data.models.DocumentScanData
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

class ClamAvDocumentScanSession(
    private val connection: ClamAvConnection,
    private val version: String,
    private val capacity: Semaphore,
) : DocumentScanSession {
    private val closed = AtomicBoolean()
    private var finished = false
    private var sentBytes = 0L

    override fun write(bytes: ByteArray) {
        check(!closed.get() && !finished)
        require(bytes.size in 1..1048576 && sentBytes + bytes.size <= 104857600)
        connection.write(ByteBuffer.allocate(4).putInt(bytes.size).flip())
        connection.write(ByteBuffer.wrap(bytes))
        sentBytes += bytes.size
    }

    override fun finish(): DocumentScanData {
        check(!closed.get() && !finished)
        finished = true
        connection.write(ByteBuffer.allocate(4).putInt(0).flip())
        val reply = connection.readFrame()
        return when {
            reply == "stream: OK" -> DocumentScanData(true, version)
            reply.startsWith("stream: ") && reply.endsWith(" FOUND") ->
                DocumentScanData(false, version)
            else -> throw DocumentScannerProtocolException()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true))
            try {
                connection.close()
            } finally {
                capacity.release()
            }
    }
}
