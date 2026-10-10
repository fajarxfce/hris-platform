import { InvalidHttpResponseError } from "./http-response-error";

/** Owns and bounds the response stream, including responses without Content-Length. */
export async function readTextResponse(
  response: Response,
  maximumBytes: number,
  mediaTypes: readonly string[],
): Promise<string> {
  const declared = Number(response.headers.get("content-length"));
  const mediaType = response.headers.get("content-type")?.split(";", 1)[0]?.trim().toLowerCase();
  if (
    !Number.isSafeInteger(maximumBytes) ||
    maximumBytes < 1 ||
    declared > maximumBytes ||
    !response.body ||
    !mediaType ||
    !mediaTypes.includes(mediaType)
  ) {
    await response.body?.cancel();
    throw new InvalidHttpResponseError();
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let bytes = 0;
  let text = "";
  let complete = false;
  try {
    for (;;) {
      const chunk = await reader.read();
      if (chunk.done) complete = true;
      else {
        bytes += chunk.value.byteLength;
        if (bytes > maximumBytes) throw new InvalidHttpResponseError();
      }
      try {
        text += decoder.decode(chunk.value, { stream: !chunk.done });
      } catch {
        throw new InvalidHttpResponseError();
      }
      if (chunk.done) return text;
    }
  } finally {
    try {
      if (!complete) await reader.cancel();
    } finally {
      reader.releaseLock();
    }
  }
}
