package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.errors.*
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

class ClamAvDocumentScanDataSource(private val settings: ClamAvSettings) : DocumentScanDataSource {
    private val capacity = Semaphore(settings.concurrency)

    override fun open(): DocumentScanSession {
        if (!capacity.tryAcquire()) throw DocumentScannerCapacityException()
        var stream: ClamAvConnection? = null
        try {
            val started = System.nanoTime()
            val version =
                ClamAvConnection.connect(settings, started).use { connection ->
                    connection.write(
                        ByteBuffer.wrap("zVERSION\u0000".toByteArray(Charsets.US_ASCII))
                    )
                    connection.readFrame(
                        minOf(settings.responseTimeout, java.time.Duration.ofSeconds(5))
                    )
                }
            if (!version.startsWith("ClamAV ") || version.length > 200)
                throw DocumentScannerProtocolException()
            stream = ClamAvConnection.connect(settings, started)
            stream.write(ByteBuffer.wrap("zINSTREAM\u0000".toByteArray(Charsets.US_ASCII)))
            return ClamAvDocumentScanSession(stream, version, capacity)
        } catch (failure: Throwable) {
            try {
                stream?.close()
            } catch (closing: Throwable) {
                failure.addSuppressed(closing)
            } finally {
                capacity.release()
            }
            throw failure
        }
    }
}
