import { maximumBinaryFileBytes } from "../../domain/files/binary-file";
import type { HttpClient, HttpRequest } from "./http-client";
import {
  HttpResponseError,
  InvalidHttpResponseError,
  RequestBodyTooLargeError,
} from "./http-response-error";
import { readBinaryResponse } from "./read-binary-response";
import { readJsonResponse } from "./read-json-response";
import { readTextResponse } from "./read-text-response";

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
    const representation = request.response;
    const budget =
      representation && "byteLength" in representation
        ? representation.byteLength
        : representation?.maximumBytes;
    if (
      budget !== undefined &&
      (!Number.isSafeInteger(budget) ||
        budget < 1 ||
        budget > (representation?.type === "binary" ? maximumBinaryFileBytes : maximumBytes))
    )
      throw new RangeError("Invalid response budget");
    if (
      representation?.type === "binary" &&
      (("etag" in representation &&
        (representation.etag.length < 1 || representation.etag.length > 512)) ||
        !/^[a-z0-9!#$&^_.+-]+\/[a-z0-9!#$&^_.+-]+$/u.test(representation.mediaType))
    )
      throw new TypeError("Invalid binary representation");
    const timeout = request.timeoutMilliseconds ?? this.deadlineMilliseconds;
    if (!Number.isInteger(timeout) || timeout < 1 || timeout > 60_000)
      throw new RangeError("Invalid request deadline");
    const method = request.method ?? "GET";
    const upload = request.binaryBody;
    const uploadHeaders =
      upload &&
      Object.freeze({
        "Upload-Offset": upload.headers["Upload-Offset"],
        "Upload-Checksum-Sha256": upload.headers["Upload-Checksum-Sha256"],
      });
    if (
      upload &&
      (request.body !== undefined ||
        !["POST", "PUT", "PATCH"].includes(method) ||
        !(upload.bytes instanceof ArrayBuffer) ||
        Object.keys(upload.headers).length !== 2 ||
        Object.keys(upload.headers).some(
          (name) => name !== "Upload-Offset" && name !== "Upload-Checksum-Sha256",
        ) ||
        typeof uploadHeaders?.["Upload-Offset"] !== "string" ||
        typeof uploadHeaders?.["Upload-Checksum-Sha256"] !== "string" ||
        !/^(0|[1-9][0-9]{0,9})$/u.test(uploadHeaders?.["Upload-Offset"] ?? "") ||
        !/^[a-f0-9]{64}$/u.test(uploadHeaders?.["Upload-Checksum-Sha256"] ?? ""))
    )
      throw new TypeError("Invalid binary request");
    if (upload && (upload.bytes.byteLength < 1 || upload.bytes.byteLength > maximumBytes))
      throw new RequestBodyTooLargeError();
    const json = request.body === undefined ? undefined : JSON.stringify(request.body);
    if (json !== undefined && new TextEncoder().encode(json).byteLength > maximumBytes)
      throw new RequestBodyTooLargeError();
    // Snapshot a bounded body before waiting for CSRF. The caller retains ownership of its part.
    const binary = upload?.bytes.slice(0);
    const body = binary ?? json;
    const controller = new AbortController();
    const onAbort = () => controller.abort(signal.reason);
    signal.addEventListener("abort", onAbort, { once: true });
    const deadline = setTimeout(
      () => controller.abort(new DOMException("Request timed out", "TimeoutError")),
      timeout,
    );
    try {
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
      if (body !== undefined) {
        headers.set("Content-Type", upload ? "application/octet-stream" : "application/json");
      }
      if (uploadHeaders) {
        headers.set("Upload-Offset", uploadHeaders["Upload-Offset"]);
        headers.set("Upload-Checksum-Sha256", uploadHeaders["Upload-Checksum-Sha256"]);
      }
      if (request.operationId) headers.set("Idempotency-Key", request.operationId);
      if (request.response) headers.set("Accept", request.response.mediaType);
      return await this.send(
        request.path,
        method,
        headers,
        body,
        controller.signal,
        request.response,
      );
    } finally {
      if (binary) new Uint8Array(binary).fill(0);
      clearTimeout(deadline);
      signal.removeEventListener("abort", onAbort);
    }
  }

  private async send(
    path: string,
    method: string,
    headers: Headers,
    body: string | ArrayBuffer | undefined,
    signal: AbortSignal,
    representation?: HttpRequest["response"],
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
    let value: unknown;
    if (!response.ok || !representation) value = await readJsonResponse(response, maximumBytes);
    else if (representation.type === "binary") {
      const content = await readBinaryResponse(response, representation, signal);
      try {
        signal.throwIfAborted();
        return content;
      } catch (error) {
        for (const part of content.parts) new Uint8Array(part).fill(0);
        throw error;
      }
    } else
      value = await readTextResponse(response, representation.maximumBytes, [
        representation.mediaType,
      ]);
    signal.throwIfAborted();
    if (!response.ok) throw new HttpResponseError(response.status, value);
    return value;
  }
}
