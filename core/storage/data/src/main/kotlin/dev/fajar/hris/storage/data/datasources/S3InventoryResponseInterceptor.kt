package dev.fajar.hris.storage.data.datasources

import dev.fajar.hris.storage.data.errors.InvalidObjectStorageResponse
import java.io.InputStream
import java.util.Optional
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.http.Abortable
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request

/** Protects the finite listing response before the SDK allocates XML entry objects. */
class S3InventoryResponseInterceptor : ExecutionInterceptor {
    override fun modifyHttpResponseContent(
        context: Context.ModifyHttpResponse,
        executionAttributes: ExecutionAttributes,
    ): Optional<InputStream> {
        if (context.request() !is ListObjectsV2Request) return context.responseBody()
        val body = context.responseBody().orElse(null) ?: return context.responseBody()
        val abortable = body as? Abortable
        if (abortable == null) {
            body.close()
            throw InvalidObjectStorageResponse()
        }
        val size =
            context
                .httpResponse()
                .firstMatchingHeader("Content-Length")
                .orElse(null)
                ?.toLongOrNull()
        val encoding =
            context.httpResponse().firstMatchingHeader("Content-Encoding").orElse("identity")
        if (
            (size != null && (size < 0 || size > 524288)) ||
                !encoding.equals("identity", ignoreCase = true)
        ) {
            abortable.abort()
            throw InvalidObjectStorageResponse()
        }
        return Optional.of(BoundedInventoryInputStream(body, abortable))
    }
}
