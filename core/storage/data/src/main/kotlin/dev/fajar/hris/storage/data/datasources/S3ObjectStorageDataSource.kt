package dev.fajar.hris.storage.data.datasources

import dev.fajar.hris.storage.data.errors.InvalidObjectStorageResponse
import dev.fajar.hris.storage.data.errors.StoredObjectVersionChanged
import dev.fajar.hris.storage.data.models.ObjectMetadataData
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.Semaphore
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*

class S3ObjectStorageDataSource(
    private val client: S3Client,
    private val bucket: String,
    private val readBudget: Duration = Duration.ofSeconds(20),
) : ObjectStorageDataSource {
    init {
        require(readBudget.toMillis() in 100..30000)
    }

    private val capacity = Semaphore(8)

    override fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData =
        withObjectStorageCapacity(capacity) {
            val response =
                client.putObject(
                    PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/octet-stream")
                        .contentLength(bytes.size.toLong())
                        .metadata(mapOf("sha256" to sha256))
                        .build(),
                    RequestBody.fromBytes(bytes),
                )
            ObjectMetadataData(bytes.size.toLong(), requireNotNull(response.eTag()), sha256)
        }

    override fun metadata(key: String): ObjectMetadataData =
        withObjectStorageCapacity(capacity) {
            val response =
                client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
            ObjectMetadataData(
                response.contentLength(),
                requireNotNull(response.eTag()),
                response.metadata()["sha256"],
            )
        }

    override fun read(key: String, offset: Long, length: Int, etag: String): ByteArray =
        withObjectStorageCapacity(capacity) {
            val deadline = System.nanoTime() + readBudget.toNanos()
            // Keep body consumption inside the SDK call deadline and its owned stream lifecycle.
            client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range("bytes=$offset-${offset+length-1}")
                    .ifMatch(etag)
                    .build(),
                ResponseTransformer<GetObjectResponse, ByteArray> { response, stream ->
                    try {
                        if (response.eTag() != etag) throw StoredObjectVersionChanged()
                        if (
                            response
                                .contentRange()
                                ?.startsWith("bytes $offset-${offset+length-1}/") != true ||
                                response.contentLength() != length.toLong()
                        )
                            throw InvalidObjectStorageResponse()
                        val bytes = ByteArray(length)
                        var read = 0
                        while (read < length) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            if (System.nanoTime() >= deadline)
                                throw SocketTimeoutException("Object read deadline exceeded")
                            val amount = stream.read(bytes, read, minOf(length - read, 65536))
                            if (amount <= 0) throw InvalidObjectStorageResponse()
                            read += amount
                        }
                        if (System.nanoTime() >= deadline)
                            throw SocketTimeoutException("Object read deadline exceeded")
                        if (stream.read() != -1) throw InvalidObjectStorageResponse()
                        bytes
                    } catch (failure: Throwable) {
                        // Closing an unread response may drain it. Abort its connection before the
                        // SDK closes the stream, preserving both cancellation and the original
                        // error.
                        try {
                            stream.abort()
                        } catch (closingFailure: Throwable) {
                            failure.addSuppressed(closingFailure)
                        }
                        throw failure
                    }
                },
            )
        }

    override fun delete(key: String) {
        withObjectStorageCapacity(capacity) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
        }
    }

    override fun close() = client.close()
}
