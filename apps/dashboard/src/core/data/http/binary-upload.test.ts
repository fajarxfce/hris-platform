import { afterEach, describe, expect, it, vi } from "vitest";
import { FetchHttpClient } from "./fetch-http-client";
import type { HttpRequest } from "./http-client";
import { RequestBodyTooLargeError } from "./http-response-error";
import { safeHttpCall } from "./safe-http-call";

const checksum = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81";
const signal = () => new AbortController().signal;
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } });
function command(): HttpRequest & { binaryBody: NonNullable<HttpRequest["binaryBody"]> } {
  return {
    path: "/api/v1/companies/example/documents/revisions/example/chunks",
    method: "POST",
    operationId: "original-operation",
    binaryBody: {
      bytes: Uint8Array.from([1, 2, 3]).buffer,
      headers: { "Upload-Offset": "0", "Upload-Checksum-Sha256": checksum },
    },
  };
}
afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe("bounded binary command transport", () => {
  it("snapshots bytes and upload headers before CSRF, retains cookie scope and clears its copy", async () => {
    const request = command(),
      original = request.binaryBody.bytes;
    let posted: ArrayBuffer | undefined,
      observed: number[] = [];
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (path, options) => {
      if (String(path).endsWith("/csrf")) {
        new Uint8Array(original).fill(9);
        (request.binaryBody.headers as { "Upload-Offset": string })["Upload-Offset"] = "1024";
        return json({ headerName: "X-CSRF-TOKEN", token: "current-token" });
      }
      posted = options?.body as ArrayBuffer;
      observed = Array.from(new Uint8Array(posted));
      const headers = new Headers(options?.headers);
      expect(headers.get("Content-Type")).toBe("application/octet-stream");
      expect(headers.get("Accept")).toBe("application/json");
      expect(headers.get("Upload-Offset")).toBe("0");
      expect(headers.get("Upload-Checksum-Sha256")).toBe(checksum);
      expect(headers.get("X-CSRF-TOKEN")).toBe("current-token");
      expect(headers.get("Idempotency-Key")).toBe("original-operation");
      expect(headers.get("X-HRIS-Client-Platform")).toBe("WEB");
      expect(headers.get("X-HRIS-Client-Build")).toBe("42");
      expect(options).toMatchObject({
        credentials: "same-origin",
        redirect: "error",
        cache: "no-store",
      });
      return json({ uploadedBytes: 3, version: 1 });
    });
    expect(
      await new FetchHttpClient(fetcher, { clientBuild: 42 }).request(request, signal()),
    ).toEqual({ uploadedBytes: 3, version: 1 });
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(observed).toEqual([1, 2, 3]);
    expect(posted).not.toBe(original);
    expect(new Uint8Array(posted ?? new ArrayBuffer(0))).toEqual(Uint8Array.from([0, 0, 0]));
    expect(new Uint8Array(original)).toEqual(Uint8Array.from([9, 9, 9]));
  });

  it.each([0, 1024 * 1024 + 1])(
    "rejects a %i-byte upload before acquiring CSRF",
    async (length) => {
      const fetcher = vi.fn<typeof fetch>(),
        request = command();
      await expect(
        new FetchHttpClient(fetcher).request(
          { ...request, binaryBody: { ...request.binaryBody, bytes: new ArrayBuffer(length) } },
          signal(),
        ),
      ).rejects.toBeInstanceOf(RequestBodyTooLargeError);
      expect(fetcher).not.toHaveBeenCalled();
    },
  );

  it("rejects mixed encodings, invalid methods and header injection before any request", async () => {
    const fetcher = vi.fn<typeof fetch>(),
      source = new FetchHttpClient(fetcher),
      request = command();
    const invalid: HttpRequest[] = [
      { ...request, body: { hidden: "json" } },
      { ...request, method: "GET" },
      { ...request, method: "DELETE" },
      {
        ...request,
        binaryBody: {
          ...request.binaryBody,
          headers: { ...request.binaryBody.headers, "Upload-Offset": "-1" },
        },
      },
      {
        ...request,
        binaryBody: {
          ...request.binaryBody,
          headers: { ...request.binaryBody.headers, "Upload-Offset": "0\r\nCookie: secret" },
        },
      },
      {
        ...request,
        binaryBody: {
          ...request.binaryBody,
          headers: { ...request.binaryBody.headers, "Upload-Checksum-Sha256": "not-a-digest" },
        },
      },
      {
        ...request,
        binaryBody: {
          ...request.binaryBody,
          headers: { ...request.binaryBody.headers, Authorization: "secret" } as NonNullable<
            HttpRequest["binaryBody"]
          >["headers"],
        },
      },
    ];
    for (const value of invalid)
      await expect(source.request(value, signal())).rejects.toBeInstanceOf(TypeError);
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("keeps stable server failures without retrying and clears the submitted copy", async () => {
    let posted: ArrayBuffer | undefined;
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-TOKEN", token: "token" }))
      .mockImplementationOnce(async (_path, options) => {
        posted = options?.body as ArrayBuffer;
        return json(
          {
            code: "document_chunk_in_progress",
            fields: { retryAt: "2026-10-12T12:00:00Z" },
            detail: "PRIVATE UPLOAD ERROR",
          },
          409,
        );
      });
    const pending = signal();
    expect(
      await safeHttpCall(pending, () => new FetchHttpClient(fetcher).request(command(), pending)),
    ).toEqual({
      ok: false,
      failure: {
        code: "document_chunk_in_progress",
        fields: { retryAt: "2026-10-12T12:00:00Z" },
        parameters: {},
      },
    });
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(new Uint8Array(posted ?? new ArrayBuffer(0))).toEqual(Uint8Array.from([0, 0, 0]));
  });

  it.each(["cancel", "deadline"] as const)(
    "clears a pending upload after %s and removes owned listeners",
    async (kind) => {
      vi.useFakeTimers();
      let enter!: () => void, posted: ArrayBuffer | undefined;
      const entered = new Promise<void>((done) => {
        enter = done;
      });
      const owner = new AbortController(),
        removed = vi.spyOn(owner.signal, "removeEventListener");
      const fetcher = vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(json({ headerName: "X-CSRF-TOKEN", token: "token" }))
        .mockImplementationOnce(
          (_path, options) =>
            new Promise((_resolve, reject) => {
              posted = options?.body as ArrayBuffer;
              options?.signal?.addEventListener("abort", () => reject(options.signal?.reason), {
                once: true,
              });
              enter();
            }),
        );
      const result = safeHttpCall(owner.signal, () =>
        new FetchHttpClient(fetcher, { deadlineMilliseconds: 100 }).request(
          command(),
          owner.signal,
        ),
      );
      const cancelled =
        kind === "cancel" ? expect(result).rejects.toMatchObject({ name: "AbortError" }) : null;
      await entered;
      if (kind === "cancel") {
        owner.abort();
        await cancelled;
      } else {
        await vi.advanceTimersByTimeAsync(100);
        expect(await result).toMatchObject({ ok: false, failure: { code: "request_timeout" } });
      }
      expect(new Uint8Array(posted ?? new ArrayBuffer(0))).toEqual(Uint8Array.from([0, 0, 0]));
      expect(removed).toHaveBeenCalledWith("abort", expect.any(Function));
      expect(vi.getTimerCount()).toBe(0);
      expect(fetcher).toHaveBeenCalledTimes(2);
    },
  );

  it("does not send bytes after cancellation during CSRF acquisition", async () => {
    const owner = new AbortController(),
      request = command();
    const copied = request.binaryBody.bytes.slice(0);
    vi.spyOn(request.binaryBody.bytes, "slice").mockReturnValue(copied);
    const fetcher = vi.fn<typeof fetch>().mockImplementationOnce(async () => {
      owner.abort();
      return json({ headerName: "X-CSRF-TOKEN", token: "token" });
    });
    await expect(new FetchHttpClient(fetcher).request(request, owner.signal)).rejects.toMatchObject(
      { name: "AbortError" },
    );
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(new Uint8Array(copied)).toEqual(Uint8Array.from([0, 0, 0]));
    expect(new Uint8Array(request.binaryBody.bytes)).toEqual(Uint8Array.from([1, 2, 3]));
  });
});
