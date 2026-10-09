import { afterEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { FetchHttpClient } from "./fetch-http-client";
import { HttpResponseError, InvalidHttpResponseError } from "./http-response-error";
import { readJsonResponse } from "./read-json-response";
import { safeHttpCall } from "./safe-http-call";

const signal = () => new AbortController().signal;
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

afterEach(() => vi.useRealTimers());

describe("cookie API transport", () => {
  it("uses current CSRF and preserves the operation ID without retrying a failed command", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json({ token: "first", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(json({ code: "stale_version" }, 409))
      .mockResolvedValueOnce(json({ token: "second", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(json({ id: "accepted" }));
    const source = new FetchHttpClient(fetcher);
    const command = {
      path: "/api/v1/example",
      method: "POST" as const,
      body: { expectedVersion: 4 },
      operationId: "d09f42bb-14a8-4bb8-800d-998193762a34",
    };
    await expect(source.request(command, signal())).rejects.toBeInstanceOf(HttpResponseError);
    expect(fetcher).toHaveBeenCalledTimes(2);
    await expect(source.request(command, signal())).resolves.toEqual({ id: "accepted" });
    for (const [index, token] of [
      [1, "first"],
      [3, "second"],
    ] as const) {
      const [, options] = fetcher.mock.calls[index] ?? [];
      const headers = new Headers(options?.headers);
      expect(headers.get("X-CSRF-TOKEN")).toBe(token);
      expect(headers.get("Idempotency-Key")).toBe(command.operationId);
      expect(options?.credentials).toBe("same-origin");
      expect(options?.redirect).toBe("error");
      expect(options?.body).toBe('{"expectedVersion":4}');
    }
  });

  it("propagates caller cancellation and releases the deadline and abort listener", async () => {
    vi.useFakeTimers();
    const controller = new AbortController();
    const removed = vi.spyOn(controller.signal, "removeEventListener");
    const fetcher = vi.fn<typeof fetch>().mockImplementation(
      (_url, options) =>
        new Promise((_resolve, reject) => {
          options?.signal?.addEventListener("abort", () => reject(options.signal?.reason), {
            once: true,
          });
        }),
    );
    const source = new FetchHttpClient(fetcher);
    const pending = safeHttpCall(controller.signal, () =>
      source.request({ path: "/api/v1/me" }, controller.signal),
    );
    const aborted = expect(pending).rejects.toMatchObject({ name: "AbortError" });
    controller.abort();
    await aborted;
    expect(vi.getTimerCount()).toBe(0);
    expect(removed).toHaveBeenCalledWith("abort", expect.any(Function));
  });

  it("classifies a deadline separately from caller cancellation", async () => {
    vi.useFakeTimers();
    const fetcher = vi.fn<typeof fetch>().mockImplementation(
      (_url, options) =>
        new Promise((_resolve, reject) => {
          options?.signal?.addEventListener("abort", () => reject(options.signal?.reason), {
            once: true,
          });
        }),
    );
    const source = new FetchHttpClient(fetcher, 200);
    const requestSignal = signal();
    const pending = safeHttpCall(requestSignal, () =>
      source.request({ path: "/api/v1/me" }, requestSignal),
    );
    await vi.advanceTimersByTimeAsync(200);
    expect(await pending).toMatchObject({ ok: false, failure: { code: "request_timeout" } });
    expect(vi.getTimerCount()).toBe(0);
  });

  it("does not acquire CSRF or send a request after cancellation", async () => {
    const controller = new AbortController();
    controller.abort();
    const fetcher = vi.fn<typeof fetch>();
    await expect(
      new FetchHttpClient(fetcher).request(
        { path: "/api/v1/example", method: "POST" },
        controller.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("rejects external paths and obsolete results", async () => {
    const controller = new AbortController();
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () => {
      controller.abort();
      return json({ id: "obsolete" });
    });
    const source = new FetchHttpClient(fetcher);
    await expect(
      source.request({ path: "https://example.test/api/v1/me" }, signal()),
    ).rejects.toBeInstanceOf(TypeError);
    expect(fetcher).not.toHaveBeenCalled();
    await expect(source.request({ path: "/api/v1/me" }, controller.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});

describe("response and repository boundaries", () => {
  it("bounds chunked bodies and cancels the unread stream", async () => {
    const cancel = vi.fn();
    const stream = new ReadableStream({
      start: (controller) => {
        controller.enqueue(new TextEncoder().encode('{"data":"'));
        controller.enqueue(new TextEncoder().encode("x".repeat(128)));
      },
      cancel,
    });
    const response = new Response(stream, { headers: { "Content-Type": "application/json" } });
    await expect(readJsonResponse(response, 64)).rejects.toBeInstanceOf(InvalidHttpResponseError);
    expect(cancel).toHaveBeenCalledOnce();
    expect(stream.locked).toBe(false);
  });

  it("does not echo technical problem messages and keeps stable localization parameters", async () => {
    const correlationId = "d09f42bb-14a8-4bb8-800d-998193762a34";
    const response = await safeHttpCall(signal(), async () => {
      throw new HttpResponseError(422, {
        code: "invalid_payroll_period",
        fields: { month: "out_of_range" },
        parameters: { maximum: "2100" },
        correlationId,
        detail: "Private SQL or employee data",
        title: "Untrusted server text",
      });
    });
    expect(response).toEqual({
      ok: false,
      failure: {
        code: "invalid_payroll_period",
        fields: { month: "out_of_range" },
        parameters: { maximum: "2100" },
        correlationId,
      },
    });
    expect(JSON.stringify(response)).not.toContain("Private");
  });

  it("maps malformed DTOs inside the boundary and never changes a mutation version", async () => {
    const response = await safeHttpCall(signal(), async () =>
      z.object({ version: z.number().int() }).parse({ version: "bad" }),
    );
    expect(response).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    const conflict = await safeHttpCall(signal(), async () => {
      throw new HttpResponseError(409, { code: "stale_employment_version" });
    });
    expect(conflict).toMatchObject({ ok: false, failure: { code: "stale_employment_version" } });
  });

  it("discards late results and late failures after a company switch", async () => {
    for (const fail of [false, true]) {
      const controller = new AbortController();
      const pending = safeHttpCall(controller.signal, async () => {
        controller.abort();
        if (fail) throw new HttpResponseError(500, { code: "request_failed" });
        return { employee: "former company" };
      });
      await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    }
  });
});
