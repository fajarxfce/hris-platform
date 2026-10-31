package dev.fajar.hris.collaboration.data.matrix

import java.io.ByteArrayOutputStream
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

/** Bounded control responses, including chunked bodies and failure responses. */
class BoundedMatrixBodySubscriber(private val maximumBytes: Int = 65536) :
    HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val bytes = ByteArrayOutputStream()
    @Volatile private var subscription: Flow.Subscription? = null

    init {
        require(maximumBytes in 1..1_048_576)
        result.whenComplete { _, error -> if (error != null) subscription?.cancel() }
    }

    override fun getBody(): CompletionStage<ByteArray> = result

    override fun onSubscribe(value: Flow.Subscription) {
        if (subscription != null || result.isDone) {
            value.cancel()
            return
        }
        subscription = value
        if (result.isDone) value.cancel() else value.request(1)
    }

    override fun onNext(items: List<ByteBuffer>) {
        if (result.isDone) return
        for (item in items) {
            if (item.remaining() > maximumBytes - bytes.size()) {
                result.completeExceptionally(MatrixResponseException())
                return
            }
            val chunk = ByteArray(item.remaining())
            item.get(chunk)
            bytes.write(chunk)
        }
        subscription?.request(1)
    }

    override fun onError(error: Throwable) {
        result.completeExceptionally(error)
    }

    override fun onComplete() {
        result.complete(bytes.toByteArray())
    }
}
