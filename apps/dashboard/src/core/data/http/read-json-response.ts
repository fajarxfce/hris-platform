import { InvalidHttpResponseError } from "./http-response-error";

/** Reads at most the configured byte budget, including chunked responses. */
export async function readJsonResponse(response: Response, maximumBytes: number): Promise<unknown> {
  if (response.status === 204) return null;
  const declared = Number(response.headers.get("content-length"));
  const mediaType = response.headers.get("content-type")?.split(";", 1)[0]?.trim();
  if (
    declared > maximumBytes ||
    !response.body ||
    (mediaType !== "application/json" && mediaType !== "application/problem+json")
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
      if (chunk.done) {
        complete = true;
        text += decoder.decode();
        return JSON.parse(text) as unknown;
      }
      bytes += chunk.value.byteLength;
      if (bytes > maximumBytes) throw new InvalidHttpResponseError();
      text += decoder.decode(chunk.value, { stream: true });
    }
  } finally {
    try {
      if (!complete) await reader.cancel();
    } finally {
      reader.releaseLock();
    }
  }
}
