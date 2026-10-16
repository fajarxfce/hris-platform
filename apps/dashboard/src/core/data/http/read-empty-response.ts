import { InvalidHttpResponseError } from "./http-response-error";

/** An explicitly empty acknowledgement must not accept a different status or a body. */
export async function readEmptyResponse(response: Response, status: 202 | 204): Promise<null> {
  if (response.status !== status || Number(response.headers.get("content-length")) > 0) {
    await response.body?.cancel();
    throw new InvalidHttpResponseError();
  }
  if (!response.body) return null;
  const reader = response.body.getReader();
  let complete = false;
  try {
    for (let chunks = 0; chunks < 4; chunks++) {
      const chunk = await reader.read();
      if (chunk.done) {
        complete = true;
        return null;
      }
      if (chunk.value.byteLength > 0) throw new InvalidHttpResponseError();
    }
    throw new InvalidHttpResponseError();
  } finally {
    try {
      if (!complete) await reader.cancel();
    } finally {
      reader.releaseLock();
    }
  }
}
