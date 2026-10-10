import { afterEach, describe, expect, it, vi } from "vitest";
import { FetchHttpClient } from "./fetch-http-client";
import { InvalidHttpResponseError } from "./http-response-error";
import { readTextResponse } from "./read-text-response";
import { safeHttpCall } from "./safe-http-call";

const signal = () => new AbortController().signal;
const csv = { type: "text" as const, mediaType: "text/csv" as const, maximumBytes: 128 };
afterEach(() => vi.useRealTimers());
describe("bounded CSV transport", () => {
  it("uses the cookie transport and CSV Accept while retaining JSON problem mapping", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response("employee_number,legal_name\n", {
          headers: { "Content-Type": "text/csv;charset=UTF-8" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ code: "employee_import_access_required", detail: "Private import" }),
          { status: 403, headers: { "Content-Type": "application/problem+json" } },
        ),
      );
    const http = new FetchHttpClient(fetcher);
    const request = { path: "/api/v1/template", response: csv };
    const owner = signal();
    expect(await http.request(request, owner)).toBe("employee_number,legal_name\n");
    expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).get("Accept")).toBe("text/csv");
    expect(fetcher.mock.calls[0]?.[1]).toMatchObject({
      credentials: "same-origin",
      redirect: "error",
      cache: "no-store",
    });
    const result = await safeHttpCall(owner, () => http.request(request, owner));
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "employee_import_access_required" },
    });
    expect(JSON.stringify(result)).not.toContain("Private");
  });
  it("bounds chunked text and rejects wrong representations or malformed UTF-8", async () => {
    const cancel = vi.fn();
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode("x".repeat(129)));
      },
      cancel,
    });
    await expect(
      readTextResponse(new Response(stream, { headers: { "Content-Type": "text/csv" } }), 128, [
        "text/csv",
      ]),
    ).rejects.toBeInstanceOf(InvalidHttpResponseError);
    expect(cancel).toHaveBeenCalledOnce();
    expect(stream.locked).toBe(false);
    for (const response of [
      new Response("<html>login</html>", { headers: { "Content-Type": "text/html" } }),
      new Response(new Uint8Array([0xc3, 0x28]), { headers: { "Content-Type": "text/csv" } }),
      new Response(new Uint8Array([0xc3]), { headers: { "Content-Type": "text/csv" } }),
    ]) {
      const owner = signal();
      expect(
        await safeHttpCall(owner, () => readTextResponse(response, 128, ["text/csv"])),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
      expect(response.body?.locked).toBe(false);
    }
  });
  it("decodes UTF-8 split across chunks and retains I/O failure classification", async () => {
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new Uint8Array([0xe6]));
        controller.enqueue(new Uint8Array([0x97, 0xa5]));
        controller.close();
      },
    });
    expect(
      await readTextResponse(new Response(stream, { headers: { "Content-Type": "text/csv" } }), 3, [
        "text/csv",
      ]),
    ).toBe("日");
    expect(stream.locked).toBe(false);
    const failedStream = new ReadableStream({
      start(controller) {
        controller.error(new TypeError("Disconnected"));
      },
    });
    const owner = signal();
    expect(
      await safeHttpCall(owner, () =>
        readTextResponse(
          new Response(failedStream, { headers: { "Content-Type": "text/csv" } }),
          128,
          ["text/csv"],
        ),
      ),
    ).toMatchObject({ ok: false, failure: { code: "connection_unavailable" } });
  });
  it("rejects expanded JSON before CSRF acquisition or command delivery", async () => {
    const fetcher = vi.fn<typeof fetch>();
    const http = new FetchHttpClient(fetcher);
    const owner = signal();
    const result = await safeHttpCall(owner, () =>
      http.request(
        { path: "/api/v1/imports", method: "POST", body: { csv: "\u0000".repeat(200_000) } },
        owner,
      ),
    );
    expect(result).toMatchObject({ ok: false, failure: { code: "request_body_too_large" } });
    expect(fetcher).not.toHaveBeenCalled();
  });
  it("keeps caller cancellation and deadlines for text acquisitions", async () => {
    vi.useFakeTimers();
    const owner = new AbortController();
    const removed = vi.spyOn(owner.signal, "removeEventListener");
    const fetcher = vi.fn<typeof fetch>().mockImplementation(
      (_url, options) =>
        new Promise((_resolve, reject) => {
          options?.signal?.addEventListener("abort", () => reject(options.signal?.reason), {
            once: true,
          });
        }),
    );
    const http = new FetchHttpClient(fetcher, { deadlineMilliseconds: 100 });
    const pending = safeHttpCall(owner.signal, () =>
      http.request({ path: "/api/v1/template", response: csv }, owner.signal),
    );
    const rejected = expect(pending).rejects.toMatchObject({ name: "AbortError" });
    owner.abort();
    await rejected;
    expect(removed).toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
    const nextOwner = signal();
    const timeout = safeHttpCall(nextOwner, () =>
      http.request({ path: "/api/v1/template", response: csv }, nextOwner),
    );
    await vi.advanceTimersByTimeAsync(100);
    expect(await timeout).toMatchObject({ ok: false, failure: { code: "request_timeout" } });
    expect(vi.getTimerCount()).toBe(0);
  });
});
