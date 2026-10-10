import { afterEach, describe, expect, it, vi } from "vitest";
import { binaryFilePartBytes, maximumBinaryFileBytes } from "../../domain/files/binary-file";
import { FetchHttpClient } from "./fetch-http-client";
import { InvalidHttpResponseError } from "./http-response-error";
import { readBinaryResponse } from "./read-binary-response";
import { safeHttpCall } from "./safe-http-call";

const signal = () => new AbortController().signal;
function firstPart(parts: readonly ArrayBuffer[]): ArrayBuffer {
  const part = parts[0];
  if (!part) throw new Error("Expected an acquired block");
  return part;
}
const expected = {
  type: "binary" as const,
  mediaType: "application/pdf",
  byteLength: 4,
  etag: '"immutable-evidence"',
};
const headers = { "Content-Type": expected.mediaType, ETag: expected.etag };
afterEach(() => vi.useRealTimers());

describe("bounded binary HTTP acquisition", () => {
  it("retains cookie/client admission and JSON failures without exposing private response details", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(new Uint8Array([1, 2, 3, 4]), { headers }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ code: "leave_attachment_not_found", detail: "PRIVATE" }), {
          status: 404,
          headers: { "Content-Type": "application/problem+json" },
        }),
      );
    const client = new FetchHttpClient(fetcher, { clientBuild: 42 });
    const request = { path: "/api/v1/evidence", response: expected, timeoutMilliseconds: 60_000 };
    const response = (await client.request(request, signal())) as {
      parts: ArrayBuffer[];
      byteLength: number;
    };
    expect(response.byteLength).toBe(4);
    expect([...new Uint8Array(firstPart(response.parts))]).toEqual([1, 2, 3, 4]);
    const options = fetcher.mock.calls[0]?.[1];
    expect(options).toMatchObject({
      credentials: "same-origin",
      redirect: "error",
      cache: "no-store",
      method: "GET",
    });
    const sent = new Headers(options?.headers);
    expect(sent.get("Accept")).toBe("application/pdf");
    expect(sent.get("X-HRIS-Client-Platform")).toBe("WEB");
    expect(sent.get("X-HRIS-Client-Build")).toBe("42");
    const failure = await safeHttpCall(signal(), () => client.request(request, signal()));
    expect(failure).toMatchObject({ ok: false, failure: { code: "leave_attachment_not_found" } });
    expect(JSON.stringify(failure)).not.toContain("PRIVATE");
  });
  it("coalesces tiny fragments into a bounded list of exact owned blocks", async () => {
    let fragments = 0;
    const stream = new ReadableStream<Uint8Array>({
      pull(controller) {
        if (fragments++ < 4096) controller.enqueue(new Uint8Array(32).fill(fragments % 251));
        else controller.close();
      },
    });
    const response = await readBinaryResponse(
      new Response(stream, { headers }),
      { ...expected, byteLength: 4096 * 32 },
      signal(),
    );
    expect(response.parts.map((part) => part.byteLength)).toEqual([
      binaryFilePartBytes,
      binaryFilePartBytes,
    ]);
    expect(new Uint8Array(firstPart(response.parts))[0]).toBe(1);
    expect(stream.locked).toBe(false);
  });
  it("detaches SDK buffers instead of retaining a mutable or oversized backing allocation", async () => {
    const large = new Uint8Array(1024);
    large.set([1, 2, 3, 4], 100);
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(large.subarray(100, 104));
        controller.close();
      },
    });
    const response = await readBinaryResponse(
      new Response(stream, { headers }),
      expected,
      signal(),
    );
    large.fill(0);
    expect(response.parts[0]?.byteLength).toBe(4);
    expect([...new Uint8Array(firstPart(response.parts))]).toEqual([1, 2, 3, 4]);
  });
  it.each([
    { ETag: '"different"' },
    { "Content-Type": "text/html" },
    { "Content-Length": "5" },
    { "Content-Length": "4.0" },
    { "Content-Length": "-1" },
  ])("rejects mismatched metadata before consuming content: %j", async (override) => {
    const cancel = vi.fn();
    const stream = new ReadableStream({ cancel });
    await expect(
      readBinaryResponse(
        new Response(stream, { headers: { ...headers, ...override } }),
        expected,
        signal(),
      ),
    ).rejects.toBeInstanceOf(InvalidHttpResponseError);
    expect(cancel).toHaveBeenCalledOnce();
    expect(stream.locked).toBe(false);
  });
  it("rejects truncated or excessive content and terminates a non-progressing stream", async () => {
    await expect(
      readBinaryResponse(new Response(new Uint8Array(3), { headers }), expected, signal()),
    ).rejects.toBeInstanceOf(InvalidHttpResponseError);
    const cancel = vi.fn();
    const excessive = new ReadableStream({
      start(controller) {
        controller.enqueue(new Uint8Array(5));
      },
      cancel,
    });
    await expect(
      readBinaryResponse(new Response(excessive, { headers }), expected, signal()),
    ).rejects.toBeInstanceOf(InvalidHttpResponseError);
    expect(cancel).toHaveBeenCalledOnce();
    expect(excessive.locked).toBe(false);
    let reads = 0;
    const empty = new ReadableStream({
      pull(controller) {
        reads++;
        controller.enqueue(new Uint8Array(0));
      },
      cancel,
    });
    await expect(
      readBinaryResponse(new Response(empty, { headers }), expected, signal()),
    ).rejects.toBeInstanceOf(InvalidHttpResponseError);
    expect(reads).toBeLessThanOrEqual(18);
    expect(empty.locked).toBe(false);
  });
  it.each(["caller", "deadline"] as const)(
    "cancels a pending body read on %s and releases all owned resources",
    async (cause) => {
      vi.useFakeTimers();
      const cancel = vi.fn();
      const stream = new ReadableStream({ cancel });
      const client = new FetchHttpClient(
        vi.fn<typeof fetch>().mockResolvedValue(new Response(stream, { headers })),
      );
      const owner = new AbortController();
      const remove = vi.spyOn(owner.signal, "removeEventListener");
      const pending = client.request(
        { path: "/api/v1/evidence", response: expected, timeoutMilliseconds: 20 },
        owner.signal,
      );
      const result = expect(pending).rejects.toMatchObject({
        name: cause === "caller" ? "AbortError" : "TimeoutError",
      });
      await vi.advanceTimersByTimeAsync(1);
      if (cause === "caller") owner.abort();
      else await vi.advanceTimersByTimeAsync(20);
      await result;
      expect(cancel).toHaveBeenCalledOnce();
      expect(stream.locked).toBe(false);
      expect(remove).toHaveBeenCalledWith("abort", expect.any(Function));
      expect(vi.getTimerCount()).toBe(0);
    },
  );
  it("validates size and deadlines before I/O and retains the one-MiB text limit", async () => {
    const fetcher = vi.fn<typeof fetch>();
    const client = new FetchHttpClient(fetcher);
    for (const byteLength of [0, -1, 1.5, maximumBinaryFileBytes + 1])
      await expect(
        client.request(
          { path: "/api/v1/evidence", response: { ...expected, byteLength } },
          signal(),
        ),
      ).rejects.toThrow("budget");
    for (const timeoutMilliseconds of [0, -1, 60_001, Number.NaN])
      await expect(
        client.request(
          { path: "/api/v1/evidence", response: expected, timeoutMilliseconds },
          signal(),
        ),
      ).rejects.toThrow("deadline");
    await expect(
      client.request(
        {
          path: "/api/v1/text",
          response: { type: "text", mediaType: "text/csv", maximumBytes: 1024 * 1024 + 1 },
        },
        signal(),
      ),
    ).rejects.toThrow("budget");
    expect(fetcher).not.toHaveBeenCalled();
  });
});
