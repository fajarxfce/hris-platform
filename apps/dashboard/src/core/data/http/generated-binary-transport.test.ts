import { describe, expect, it, vi } from "vitest";
import { FetchHttpClient } from "./fetch-http-client";
import { InvalidHttpResponseError } from "./http-response-error";
import { readBinaryResponse } from "./read-binary-response";

const expected = {
  type: "binary" as const,
  mediaType: "application/pdf",
  maximumBytes: 1024 * 1024,
};
const headers = {
  "Content-Type": "application/pdf",
  "Content-Length": "4",
  ETag: '"retained-document"',
};
const signal = () => new AbortController().signal;
describe("generated binary responses", () => {
  it("acquires the declared representation in one request without a metadata preflight", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response(new Uint8Array([1, 2, 3, 4]), { headers }));
    const client = new FetchHttpClient(fetcher);
    const result = await client.request({ path: "/api/v1/payslip", response: expected }, signal());
    expect(result).toMatchObject({
      byteLength: 4,
      etag: headers.ETag,
      mediaType: headers["Content-Type"],
    });
    expect(fetcher).toHaveBeenCalledOnce();
    expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).get("Accept")).toBe("application/pdf");
  });
  it.each([
    ["missing size", { "Content-Type": "application/pdf", ETag: headers.ETag }],
    ["missing tag", { "Content-Type": "application/pdf", "Content-Length": "4" }],
    ["weak tag", { ...headers, ETag: 'W/"tag"' }],
    ["unquoted tag", { ...headers, ETag: "tag" }],
    ["excessive size", { ...headers, "Content-Length": String(1024 * 1024 + 1) }],
    ["empty size", { ...headers, "Content-Length": "0" }],
    ["invalid size", { ...headers, "Content-Length": "4.0" }],
    ["wrong media", { ...headers, "Content-Type": "text/html" }],
  ] as const)("rejects %s before reading private bytes", async (_, metadata) => {
    const cancel = vi.fn(),
      body = new ReadableStream<Uint8Array>({ cancel });
    await expect(
      readBinaryResponse(new Response(body, { headers: metadata }), expected, signal()),
    ).rejects.toBeInstanceOf(InvalidHttpResponseError);
    expect(cancel).toHaveBeenCalledOnce();
    expect(body.locked).toBe(false);
  });
  it.each([3, 5])("rejects a %i-byte body that differs from the declared size", async (size) => {
    await expect(
      readBinaryResponse(new Response(new Uint8Array(size), { headers }), expected, signal()),
    ).rejects.toBeInstanceOf(InvalidHttpResponseError);
  });
  it("cancels a partially received generated file and releases the reader", async () => {
    let ready!: () => void;
    const entered = new Promise<void>((resolve) => {
      ready = resolve;
    });
    const cancel = vi.fn(),
      owner = new AbortController();
    const body = new ReadableStream<Uint8Array>({
      start(c) {
        c.enqueue(new Uint8Array([1, 2]));
      },
      pull() {
        ready();
      },
      cancel,
    });
    const pending = readBinaryResponse(new Response(body, { headers }), expected, owner.signal);
    const rejected = expect(pending).rejects.toMatchObject({ name: "AbortError" });
    await entered;
    owner.abort();
    await rejected;
    expect(cancel).toHaveBeenCalledOnce();
    expect(body.locked).toBe(false);
  });
  it("rejects a generated response budget before performing I/O", async () => {
    const fetcher = vi.fn<typeof fetch>(),
      client = new FetchHttpClient(fetcher);
    for (const maximumBytes of [0, -1, 1.5, 100 * 1024 * 1024 + 1])
      await expect(
        client.request(
          { path: "/api/v1/payslip", response: { ...expected, maximumBytes } },
          signal(),
        ),
      ).rejects.toThrow("budget");
    expect(fetcher).not.toHaveBeenCalled();
  });
});
