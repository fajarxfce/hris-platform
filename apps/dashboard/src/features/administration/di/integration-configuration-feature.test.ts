import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId } from "../../../core/domain/identifiers";
import { integrationKinds } from "../domain/entities/integration-configuration";
import { createAdministrationFeature } from "./administration-feature";

const access = {
  accountId: "20000000-0000-4000-8000-000000000001" as AccountId,
  permissions: ["identity.manage"],
};
const dto = () => ({
  runtime: "API",
  observedAt: "2026-10-28T16:00:00Z",
  integrations: integrationKinds.map((kind) => ({ kind, enabled: kind === "STORAGE" })),
});

describe("integration configuration boundary", () => {
  it("rejects missing platform authority and invalid accounts before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const load = createAdministrationFeature({ request }).loadIntegrationConfiguration;
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
  it("reads the global endpoint and detaches every acquired flag", async () => {
    const raw = dto(),
      request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const signal = new AbortController().signal;
    const result = await createAdministrationFeature({
      request,
    }).loadIntegrationConfiguration.execute(access, signal);
    expect(request).toHaveBeenCalledWith(
      { path: "/api/v1/administration/integrations/configuration" },
      signal,
    );
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error("Expected configuration");
    expect(result.value.integrations).toHaveLength(6);
    raw.integrations[0] = { kind: "FIREBASE_AUTH", enabled: true };
    expect(result.value.integrations[0]?.enabled).toBe(false);
    expect(Object.isFrozen(result.value.integrations)).toBe(true);
    expect(result.value.integrations.every(Object.isFrozen)).toBe(true);
  });
  it.each([
    { ...dto(), runtime: "WORKER" },
    { ...dto(), observedAt: "2026-02-30T16:00:00Z" },
    { ...dto(), integrations: dto().integrations.slice(1) },
    { ...dto(), integrations: Array.from({ length: 6 }, () => dto().integrations[0]) },
    { ...dto(), credentials: "PRIVATE FIXTURE VALUE" },
    { ...dto(), integrations: dto().integrations.map((entry) => ({ ...entry, enabled: "true" })) },
  ])("rejects malformed, partial or unexpected configuration", async (raw) => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    expect(
      await createAdministrationFeature({ request }).loadIntegrationConfiguration.execute(
        access,
        new AbortController().signal,
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("preserves safe denial and cancellation after acquisition", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValue(new HttpResponseError(403, { code: "platform_administrator_required" }));
    const load = createAdministrationFeature({ request }).loadIntegrationConfiguration;
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
