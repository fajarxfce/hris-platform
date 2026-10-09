import { z } from "zod";
import type { Failure, Result } from "../../domain/result";
import { failed, success } from "../../domain/result";
import { HttpResponseError, InvalidHttpResponseError } from "./http-response-error";

const problemSchema = z.object({
  code: z.string().regex(/^[a-z][a-z0-9_]{0,99}$/u),
  fields: z.record(z.string().max(200), z.string().max(100)).default({}),
  parameters: z.record(z.string().max(100), z.string().max(1000)).default({}),
  correlationId: z.uuid().nullish(),
  retryAfterSeconds: z.number().int().min(0).max(604_800).optional().catch(undefined),
});

/** Repository boundary: both acquisition and DTO mapping belong inside the operation. */
export async function safeHttpCall<T>(
  signal: AbortSignal,
  operation: () => Promise<T>,
): Promise<Result<T>> {
  signal.throwIfAborted();
  try {
    const value = await operation();
    signal.throwIfAborted();
    return success(value);
  } catch (error) {
    signal.throwIfAborted();
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    if (error instanceof DOMException && error.name === "TimeoutError")
      return failed("request_timeout");
    if (error instanceof HttpResponseError) {
      const parsed = problemSchema.safeParse(error.problem);
      if (parsed.success) {
        const failure: Failure = {
          code: parsed.data.code,
          fields: parsed.data.fields,
          parameters: parsed.data.parameters,
          ...(parsed.data.correlationId ? { correlationId: parsed.data.correlationId } : {}),
          ...(parsed.data.retryAfterSeconds === undefined
            ? {}
            : { retryAfterSeconds: parsed.data.retryAfterSeconds }),
        };
        return { ok: false, failure };
      }
      switch (error.status) {
        case 401:
          return failed("authentication_required");
        case 403:
          return failed("access_denied");
        case 404:
          return failed("resource_not_found");
        case 409:
          return failed("request_conflict");
        case 429:
          return failed("request_rate_limited");
        default:
          return failed("request_failed");
      }
    }
    if (
      error instanceof z.ZodError ||
      error instanceof SyntaxError ||
      error instanceof InvalidHttpResponseError
    ) {
      return failed("invalid_response");
    }
    return failed(error instanceof TypeError ? "connection_unavailable" : "request_failed");
  }
}
