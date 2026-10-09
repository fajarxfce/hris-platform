import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import type { ClientPolicyRevisionDto } from "../data/models/client-policy-revision-dto";
import type { ClientPolicySettingsDto } from "../data/models/client-policy-settings-dto";
import { createAdministrationFeature } from "./administration-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["settings.manage"] };
const path = `/api/v1/companies/${companyId}/settings/client-policy`;
const signal = () => new AbortController().signal;
const revision = (version = 0): ClientPolicyRevisionDto => ({
  version,
  activateAt: "2026-10-01T00:00:00Z",
  disabledModules: ["EXPENSES"],
  minimumBuilds: { android: 3, ios: 2, web: 1 },
  maintenance: null,
  recordedAt: "2026-09-30T00:00:00Z",
  actorId: "20000000-0000-4000-8000-000000000001",
  reason: "Update client support",
});
const settings = (): ClientPolicySettingsDto => ({
  latest: revision(),
  effective: {
    schemaVersion: 1,
    version: 0,
    enabledModules: [
      "PEOPLE",
      "WORKFORCE",
      "LEAVE",
      "PAYROLL",
      "DOCUMENTS",
      "COMMUNICATIONS",
      "REPORTING",
    ],
    minimumBuilds: { android: 3, ios: 2, web: 1 },
    maintenance: null,
    maintenanceActive: false,
    serverTime: "2026-10-10T00:00:00.000000123Z",
    validUntil: "2026-10-10T00:01:00.000000123Z",
  },
});

describe("client policy feature boundaries", () => {
  it("requires settings permission and a bounded canonical revision before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createAdministrationFeature({ request });
    expect(
      await feature.loadClientPolicy.execute(
        { companyId, permissions: ["audit.read"] },
        null,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const version of ["", "-1", "10000", "01", "1.0", "1e2", " 1", "1/../../other"])
      expect(await feature.loadClientPolicy.execute(access, version, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_revision" },
      });
    const abort = new AbortController();
    abort.abort();
    await expect(feature.loadClientPolicy.execute(access, null, abort.signal)).rejects.toThrow();
    expect(request).not.toHaveBeenCalled();
  });

  it("keeps a scheduled head distinct from the effective snapshot and freezes acquired configuration", async () => {
    const dto = settings();
    dto.latest = { ...revision(1), activateAt: "2026-10-11T00:00:00Z", disabledModules: [] };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const result = await createAdministrationFeature({ request }).loadClientPolicy.execute(
      access,
      null,
      signal(),
    );
    expect(result).toMatchObject({
      ok: true,
      value: {
        settings: { companyId, latest: { version: 1 }, effective: { version: 0 } },
        selected: { companyId, version: 1 },
      },
    });
    if (!result.ok) throw new Error("Client policy fixture was rejected");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.settings)).toBe(true);
    expect(Object.isFrozen(result.value.settings.effective)).toBe(true);
    expect(Object.isFrozen(result.value.settings.effective.enabledModules)).toBe(true);
    expect(Object.isFrozen(result.value.settings.effective.minimumBuilds)).toBe(true);
    expect(Object.isFrozen(result.value.selected)).toBe(true);
    expect(Object.isFrozen(result.value.selected?.disabledModules)).toBe(true);
    dto.latest.disabledModules.push("PAYROLL");
    expect(result.value.selected?.disabledModules).toEqual([]);
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.lastCall?.[0].path).toBe(path);
  });

  it("supports default policy before the first activation and rejects incoherent or unbounded responses", async () => {
    const fallback = settings();
    fallback.latest = null;
    fallback.effective.version = null;
    fallback.effective.enabledModules.push("EXPENSES");
    fallback.effective.minimumBuilds = { android: 0, ios: 0, web: 0 };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(fallback);
    const feature = createAdministrationFeature({ request });
    expect(await feature.loadClientPolicy.execute(access, null, signal())).toMatchObject({
      ok: true,
      value: { selected: null, settings: { effective: { version: null } } },
    });
    request.mockResolvedValueOnce({
      ...fallback,
      latest: { ...revision(), activateAt: "2026-10-11T00:00:00Z" },
    });
    expect(await feature.loadClientPolicy.execute(access, null, signal())).toMatchObject({
      ok: true,
    });
    for (const change of [
      { latest: null },
      { latest: { ...revision(), disabledModules: ["EXPENSES", "EXPENSES"] } },
      { latest: { ...revision(), activateAt: "2026-02-30T00:00:00Z" } },
      { latest: { ...revision(), activateAt: "2026-10-11T00:00:00Z" } },
      { latest: { ...revision(1), activateAt: "2026-10-10T00:00:00Z" } },
      { effective: { ...settings().effective, version: 1 } },
      { effective: { ...settings().effective, version: null } },
      { effective: { ...settings().effective, schemaVersion: 2 } },
      { effective: { ...settings().effective, enabledModules: ["PEOPLE", "PEOPLE"] } },
      { effective: { ...settings().effective, enabledModules: ["PEOPLE"] } },
      { effective: { ...settings().effective, minimumBuilds: { android: -1, ios: 2, web: 1 } } },
      { effective: { ...settings().effective, minimumBuilds: { android: 4, ios: 2, web: 1 } } },
      { effective: { ...settings().effective, validUntil: "2026-10-09T23:59:59Z" } },
      { effective: { ...settings().effective, validUntil: "2026-10-10T00:01:00.000001Z" } },
      { effective: { ...settings().effective, maintenanceActive: true } },
    ]) {
      request.mockResolvedValueOnce({ ...settings(), ...change });
      expect(await feature.loadClientPolicy.execute(access, null, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("compares maintenance at database precision and keeps its end exclusive", async () => {
    const dto = settings();
    const window = {
      startsAt: "2026-10-10T00:00:00.000000Z",
      endsAt: "2026-10-10T00:00:30Z",
    };
    dto.latest = {
      ...revision(),
      maintenance: window,
    };
    dto.effective.maintenance = { ...window, startsAt: "2026-10-10T00:00:00Z" };
    dto.effective.maintenanceActive = true;
    dto.effective.validUntil = "2026-10-10T00:00:30Z";
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const feature = createAdministrationFeature({ request });
    const active = await feature.loadClientPolicy.execute(access, null, signal());
    expect(active).toMatchObject({
      ok: true,
      value: { settings: { effective: { maintenanceActive: true } } },
    });
    if (!active.ok) throw new Error("Maintenance fixture was rejected");
    expect(Object.isFrozen(active.value.settings.effective.maintenance)).toBe(true);
    dto.effective.serverTime = "2026-10-10T00:00:30Z";
    dto.effective.maintenanceActive = false;
    expect(await feature.loadClientPolicy.execute(access, null, signal())).toMatchObject({
      ok: true,
    });
    for (const endsAt of ["2026-10-10T00:00:00Z", "2026-10-17T00:00:00.000001Z"]) {
      request.mockResolvedValueOnce({
        ...dto,
        latest: { ...dto.latest, maintenance: { ...dto.latest.maintenance, endsAt } },
      });
      expect(await feature.loadClientPolicy.execute(access, null, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("reads only the named immutable revision and preserves localized failure codes without retrying", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(settings())
      .mockResolvedValueOnce(revision());
    const feature = createAdministrationFeature({ request });
    expect(await feature.loadClientPolicy.execute(access, "0", signal())).toMatchObject({
      ok: true,
      value: { selected: { version: 0, companyId } },
    });
    expect(request.mock.calls.map(([input]) => input.path)).toEqual([path, `${path}/revisions/0`]);
    request.mockResolvedValueOnce(settings()).mockResolvedValueOnce(revision(1));
    expect(await feature.loadClientPolicy.execute(access, "0", signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    request.mockResolvedValueOnce(settings()).mockRejectedValueOnce(
      new HttpResponseError(404, {
        code: "client_policy_revision_not_found",
        parameters: {},
        detail: "PRIVATE DATABASE DETAIL",
      }),
    );
    const failed = await feature.loadClientPolicy.execute(access, "9999", signal());
    expect(failed).toMatchObject({
      ok: false,
      failure: { code: "client_policy_revision_not_found" },
    });
    expect(JSON.stringify(failed)).not.toContain("PRIVATE");
    expect(request).toHaveBeenCalledTimes(6);
    request.mockRejectedValueOnce(new HttpResponseError(403, { code: "company_access_denied" }));
    expect(await feature.loadClientPolicy.execute(access, "0", signal())).toMatchObject({
      ok: false,
      failure: { code: "company_access_denied" },
    });
    expect(request).toHaveBeenCalledTimes(7);
  });

  it("cancels a pending snapshot before starting history and rejects late revision results", async () => {
    for (const duringHistory of [false, true]) {
      let resolve!: (value: unknown) => void;
      const held = new Promise<unknown>((done) => {
        resolve = done;
      });
      const request = vi.fn<HttpClient["request"]>();
      if (duringHistory) request.mockResolvedValueOnce(settings());
      request.mockReturnValueOnce(held);
      const feature = createAdministrationFeature({ request });
      const abort = new AbortController();
      const read = feature.loadClientPolicy.execute(access, "0", abort.signal);
      await vi.waitFor(() => expect(request).toHaveBeenCalledTimes(duringHistory ? 2 : 1));
      abort.abort();
      resolve(duringHistory ? revision() : settings());
      await expect(read).rejects.toThrow();
      expect(request).toHaveBeenCalledTimes(duringHistory ? 2 : 1);
      expect(request.mock.lastCall?.[1]).toBe(abort.signal);
    }
  });
});
