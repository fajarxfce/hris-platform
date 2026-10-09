package dev.fajar.hris.push.data.transport

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

/** Counts streamed bytes even without Content-Length; cancels upstream before accepting excess. */
class BoundedPushBodySubscriber(private val maximumBytes: Int) :
    HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val bytes = ByteArrayOutputStream()
    @Volatile private var subscription: Flow.Subscription? = null

    init {
        require(maximumBytes in 1..65536)
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
                result.completeExceptionally(PushResponseSizeException())
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

class PushResponseSizeException : IOException("Push response exceeds its byte limit")
