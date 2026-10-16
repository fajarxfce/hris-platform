import { expect, it, vi } from "vitest";
import { FetchHttpClient } from "./fetch-http-client";
import { InvalidHttpResponseError } from "./http-response-error";
import { readEmptyResponse } from "./read-empty-response";

it.each([202, 204] as const)(
  "accepts an explicit empty %s acknowledgement without a content type",
  async (status) => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ token: "owned-csrf", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(new Response(null, { status }));
    const http = new FetchHttpClient(fetcher);
    expect(
      await http.request(
        {
          path: "/api/v1/auth/password-recovery",
          method: "POST",
          body: { email: "owned@example.test" },
          response: { type: "empty", status },
        },
        new AbortController().signal,
      ),
    ).toBeNull();
    const headers = new Headers(fetcher.mock.calls[1]?.[1]?.headers);
    expect(headers.get("X-CSRF-TOKEN")).toBe("owned-csrf");
    expect(headers.has("Authorization")).toBe(false);
    expect(fetcher.mock.calls[1]?.[1]).toMatchObject({
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
    });
  },
);
it.each([200, 201, 204])("rejects status %s for a promised empty 202 response", async (status) => {
  await expect(readEmptyResponse(new Response(null, { status }), 202)).rejects.toBeInstanceOf(
    InvalidHttpResponseError,
  );
});
it("cancels a nonempty response before acquiring further bytes and releases its reader", async () => {
  const cancel = vi.fn();
  const body = new ReadableStream<Uint8Array>({
    start: (controller) => controller.enqueue(new Uint8Array([1])),
    cancel,
  });
  await expect(readEmptyResponse(new Response(body, { status: 202 }), 202)).rejects.toBeInstanceOf(
    InvalidHttpResponseError,
  );
  expect(cancel).toHaveBeenCalledOnce();
  expect(body.locked).toBe(false);
});
it("bounds even an endless stream of empty chunks", async () => {
  const cancel = vi.fn();
  const body = new ReadableStream<Uint8Array>({
    pull: (controller) => controller.enqueue(new Uint8Array()),
    cancel,
  });
  await expect(readEmptyResponse(new Response(body, { status: 202 }), 202)).rejects.toBeInstanceOf(
    InvalidHttpResponseError,
  );
  expect(cancel).toHaveBeenCalledOnce();
  expect(body.locked).toBe(false);
});
it("retains problem responses instead of treating them as empty acknowledgements", async () => {
  const fetcher = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(Response.json({ token: "owned-csrf", headerName: "X-CSRF-TOKEN" }))
    .mockResolvedValueOnce(Response.json({ code: "csrf_invalid" }, { status: 403 }));
  const http = new FetchHttpClient(fetcher);
  await expect(
    http.request(
      {
        path: "/api/v1/auth/password-recovery",
        method: "POST",
        response: { type: "empty", status: 202 },
      },
      new AbortController().signal,
    ),
  ).rejects.toMatchObject({ status: 403, problem: { code: "csrf_invalid" } });
});
