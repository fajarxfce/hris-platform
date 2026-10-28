import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId } from "../../../core/domain/identifiers";
import { workerIntegrationKinds } from "../domain/entities/worker-integrations";
import { createAdministrationFeature } from "./administration-feature";

const access = {
  accountId: "20000000-0000-4000-8000-000000000001" as AccountId,
  permissions: ["identity.manage"],
};
const worker = () => ({
  instanceId: "30000000-0000-4000-8000-000000000001",
  firstSeenAt: "2026-10-28T16:00:00Z",
  lastSeenAt: "2026-10-28T16:01:00Z",
  status: "RECENT",
  integrations: workerIntegrationKinds.map((kind) => ({ kind, enabled: kind === "PUSH" })),
});
const dto = () => ({ observedAt: "2026-10-28T16:01:05Z", workers: [worker()] });

describe("worker integration boundary", () => {
  it("denies invalid accounts and missing platform authority before acquisition", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const load = createAdministrationFeature({ request }).loadWorkerIntegrations;
    for (const candidate of [
      { ...access, permissions: ["settings.manage"] },
      { ...access, accountId: "invalid" as AccountId },
    ])
      expect(await load.execute(candidate, new AbortController().signal)).toMatchObject({
        ok: false,
        failure: { code: "platform_administrator_required" },
      });
    expect(request).not.toHaveBeenCalled();
  });
  it("reads the worker endpoint and detaches its nested process observations", async () => {
    const raw = dto(),
      request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const signal = new AbortController().signal;
    const result = await createAdministrationFeature({ request }).loadWorkerIntegrations.execute(
      access,
      signal,
    );
    expect(request).toHaveBeenCalledWith(
      { path: "/api/v1/administration/integrations/workers" },
      signal,
    );
    if (!result.ok) throw new Error("Expected worker reports");
    const mutableEntry = raw.workers[0]?.integrations[1];
    if (!mutableEntry) throw new Error("Expected mutable fixture integration");
    mutableEntry.enabled = false;
    expect(result.value.workers[0]?.integrations[1]?.enabled).toBe(true);
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.workers)).toBe(true);
    expect(
      result.value.workers.every(
        (entry) =>
          Object.isFrozen(entry) &&
          Object.isFrozen(entry.integrations) &&
          entry.integrations.every(Object.isFrozen),
      ),
    ).toBe(true);
  });
  it.each([
    { ...dto(), credentials: "PRIVATE" },
    { ...dto(), observedAt: "2026-02-30T16:01:05Z" },
    { ...dto(), workers: [worker(), worker()] },
    {
      ...dto(),
      workers: Array.from({ length: 65 }, (_, index) => ({
        ...worker(),
        instanceId: `30000000-0000-4000-8000-${String(index).padStart(12, "0")}`,
      })),
    },
    { ...dto(), workers: [{ ...worker(), instanceId: "invalid" }] },
    { ...dto(), workers: [{ ...worker(), status: "HEALTHY" }] },
    { ...dto(), workers: [{ ...worker(), firstSeenAt: "2026-10-28T17:00:00Z" }] },
    { ...dto(), workers: [{ ...worker(), projectId: "PRIVATE" }] },
    { ...dto(), workers: [{ ...worker(), integrations: worker().integrations.slice(1) }] },
    {
      ...dto(),
      workers: [
        { ...worker(), integrations: Array.from({ length: 4 }, () => worker().integrations[0]) },
      ],
    },
  ])("rejects oversized, inconsistent, partial or unexpected reports", async (raw) => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    expect(
      await createAdministrationFeature({ request }).loadWorkerIntegrations.execute(
        access,
        new AbortController().signal,
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("accepts the explicit absence of a report without calling it healthy", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ ...dto(), workers: [] });
    expect(
      await createAdministrationFeature({ request }).loadWorkerIntegrations.execute(
        access,
        new AbortController().signal,
      ),
    ).toMatchObject({ ok: true, value: { workers: [] } });
  });
  it("preserves authority failures and cancellation of a late response", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValue(new HttpResponseError(403, { code: "platform_administrator_required" }));
    const load = createAdministrationFeature({ request }).loadWorkerIntegrations;
    expect(await load.execute(access, new AbortController().signal)).toMatchObject({
      ok: false,
      failure: { code: "platform_administrator_required" },
    });
    const pending = new AbortController();
    request.mockImplementation(async () => {
      pending.abort();
      return dto();
    });
    await expect(load.execute(access, pending.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});
