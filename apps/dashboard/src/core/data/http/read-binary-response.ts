import { binaryFilePartBytes, maximumBinaryFileBytes } from "../../domain/files/binary-file";
import type { BinaryResponseDto } from "./binary-response-dto";
import type { HttpRequest } from "./http-client";
import { InvalidHttpResponseError } from "./http-response-error";

type Expected = Extract<NonNullable<HttpRequest["response"]>, { type: "binary" }>;

/** Copy into bounded blocks so fragmented network chunks cannot grow an unbounded buffer list. */
export async function readBinaryResponse(
  response: Response,
  expected: Expected,
  signal: AbortSignal,
): Promise<BinaryResponseDto> {
  const declared = response.headers.get("Content-Length");
  const mediaType = response.headers.get("Content-Type")?.split(";", 1)[0]?.trim().toLowerCase();
  if (
    response.status !== 200 ||
    !response.body ||
    !Number.isSafeInteger(expected.byteLength) ||
    expected.byteLength < 1 ||
    expected.byteLength > maximumBinaryFileBytes ||
    (declared !== null && (!/^\d+$/u.test(declared) || Number(declared) !== expected.byteLength)) ||
    mediaType !== expected.mediaType ||
    response.headers.get("ETag") !== expected.etag
  ) {
    await response.body?.cancel();
    signal.throwIfAborted();
    throw new InvalidHttpResponseError();
  }
  const reader = response.body.getReader();
  const parts: ArrayBuffer[] = [];
  let block: Uint8Array<ArrayBuffer> | null = null;
  let blockOffset = 0;
  let bytes = 0;
  let emptyChunks = 0;
  let complete = false;
  let cancelled: Promise<void> | undefined;
  const onAbort = () => {
    // A stream already rejected by fetch may also reject cancellation; preserve the caller's abort.
    cancelled = reader.cancel(signal.reason).catch(() => {});
  };
  signal.addEventListener("abort", onAbort, { once: true });
  try {
    for (let reads = 0; reads < 1_000_000; reads++) {
      signal.throwIfAborted();
      const chunk = await reader.read();
      signal.throwIfAborted();
      if (chunk.done) {
        complete = true;
        if (bytes !== expected.byteLength) throw new InvalidHttpResponseError();
        return { parts, byteLength: bytes, mediaType: expected.mediaType, etag: expected.etag };
      }
      if (chunk.value.byteLength === 0) {
        if (++emptyChunks > 16) throw new InvalidHttpResponseError();
        continue;
      }
      emptyChunks = 0;
      if (bytes + chunk.value.byteLength > expected.byteLength)
        throw new InvalidHttpResponseError();
      let offset = 0;
      while (offset < chunk.value.byteLength) {
        if (!block || blockOffset === block.byteLength) {
          block = new Uint8Array(Math.min(binaryFilePartBytes, expected.byteLength - bytes));
          parts.push(block.buffer);
          blockOffset = 0;
        }
        const count = Math.min(block.byteLength - blockOffset, chunk.value.byteLength - offset);
        block.set(chunk.value.subarray(offset, offset + count), blockOffset);
        blockOffset += count;
        offset += count;
        bytes += count;
      }
    }
    throw new InvalidHttpResponseError();
  } finally {
    signal.removeEventListener("abort", onAbort);
    try {
      if (cancelled) await cancelled;
      else if (!complete) await reader.cancel();
    } finally {
      reader.releaseLock();
    }
  }
}
