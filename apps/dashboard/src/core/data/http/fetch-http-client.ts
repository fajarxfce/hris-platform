import type { HttpClient, HttpRequest } from "./http-client";
import { HttpResponseError, InvalidHttpResponseError } from "./http-response-error";
import { readJsonResponse } from "./read-json-response";

const maximumBytes = 1024 * 1024;

/** Same-origin cookie transport. It owns each deadline and never retries a command. */
export class FetchHttpClient implements HttpClient {
  private readonly deadlineMilliseconds: number;
  private readonly clientBuild: number;

  constructor(
    private readonly fetcher: typeof fetch = globalThis.fetch.bind(globalThis),
    options: Readonly<{ deadlineMilliseconds?: number; clientBuild?: number }> = {},
  ) {
    this.deadlineMilliseconds = options.deadlineMilliseconds ?? 15_000;
    this.clientBuild = options.clientBuild ?? 1;
    if (
      !Number.isInteger(this.deadlineMilliseconds) ||
      this.deadlineMilliseconds < 1 ||
      this.deadlineMilliseconds > 60_000
    ) {
      throw new RangeError("Invalid request deadline");
    }
    if (
      !Number.isInteger(this.clientBuild) ||
      this.clientBuild < 0 ||
      this.clientBuild > 999_999_999
    ) {
      throw new RangeError("Invalid client build");
    }
  }

  async request(request: HttpRequest, signal: AbortSignal): Promise<unknown> {
    signal.throwIfAborted();
    if (
      !request.path.startsWith("/api/v1/") ||
      /[\\\r\n#]/u.test(request.path) ||
      !new URL(request.path, "https://api.invalid").pathname.startsWith("/api/v1/")
    ) {
      throw new TypeError("Invalid API path");
    }
    const controller = new AbortController();
    const onAbort = () => controller.abort(signal.reason);
    signal.addEventListener("abort", onAbort, { once: true });
    const deadline = setTimeout(
      () => controller.abort(new DOMException("Request timed out", "TimeoutError")),
      this.deadlineMilliseconds,
    );
    try {
      const method = request.method ?? "GET";
      const headers = new Headers({
        Accept: "application/json",
        "X-HRIS-Client-Platform": "WEB",
        "X-HRIS-Client-Build": String(this.clientBuild),
      });
      if (method !== "GET") {
        // Login, logout and MFA rotate CSRF state; acquire the current token for each mutation.
        const csrf = await this.send(
          "/api/v1/auth/csrf",
          "GET",
          headers,
          undefined,
          controller.signal,
        );
        if (
          !csrf ||
          typeof csrf !== "object" ||
          !("token" in csrf) ||
          typeof csrf.token !== "string" ||
          csrf.token.length > 4096 ||
          !("headerName" in csrf) ||
          csrf.headerName !== "X-CSRF-TOKEN"
        ) {
          throw new InvalidHttpResponseError();
        }
        headers.set(csrf.headerName, csrf.token);
      }
      let body: string | undefined;
      if (request.body !== undefined) {
        body = JSON.stringify(request.body);
        if (new TextEncoder().encode(body).byteLength > maximumBytes) {
          throw new RangeError("Request body is too large");
        }
        headers.set("Content-Type", "application/json");
      }
      if (request.operationId) headers.set("Idempotency-Key", request.operationId);
      return await this.send(request.path, method, headers, body, controller.signal);
    } finally {
      clearTimeout(deadline);
      signal.removeEventListener("abort", onAbort);
    }
  }

  private async send(
    path: string,
    method: string,
    headers: Headers,
    body: string | undefined,
    signal: AbortSignal,
  ): Promise<unknown> {
    signal.throwIfAborted();
    const response = await this.fetcher(path, {
      method,
      headers,
      ...(body === undefined ? {} : { body }),
      signal,
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
    });
    const value = await readJsonResponse(response, maximumBytes);
    signal.throwIfAborted();
    if (!response.ok) throw new HttpResponseError(response.status, value);
    return value;
  }
}
