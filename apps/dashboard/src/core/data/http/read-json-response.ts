import { readTextResponse } from "./read-text-response";

/** Reads at most the configured byte budget, including chunked responses. */
export async function readJsonResponse(response: Response, maximumBytes: number): Promise<unknown> {
  if (response.status === 204) return null;
  return JSON.parse(
    await readTextResponse(response, maximumBytes, [
      "application/json",
      "application/problem+json",
    ]),
  ) as unknown;
}
