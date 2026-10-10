import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { ClientPolicyChange } from "../domain/entities/client-policy-change";
import { createAdministrationFeature } from "./administration-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const operation = "30000000-0000-4000-8000-000000000001" as OperationId;
const access = { companyId, permissions: ["settings.manage"] };
const path = `/api/v1/companies/${companyId}/settings/client-policy`;
const signal = () => new AbortController().signal;
const input = (overrides: Partial<ClientPolicyChange> = {}): ClientPolicyChange => ({
  expectedVersion: null,
  activateAt: null,
  disabledModules: [],
  minimumBuilds: { android: 0, ios: 0, web: 0 },
  maintenance: null,
  reason: "Client support policy",
  ...overrides,
});

describe("client policy command boundary", () => {
  it("requires settings access, a valid operation and a live request before acquisition", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const save = createAdministrationFeature({ request }).saveClientPolicy;
    for (const permissions of [[], ["company.manage"], ["audit.read"]])
      expect(
        await save.execute({ companyId, permissions }, operation, input(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(await save.execute(access, "other" as OperationId, input(), signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_client_policy" },
    });
    const abort = new AbortController();
    abort.abort();
    expect(() => save.execute(access, operation, input(), abort.signal)).toThrow();
    expect(request).not.toHaveBeenCalled();
  });

  it("validates bounded revisions, modules, builds, UTC dates and safe reasons before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const save = createAdministrationFeature({ request }).saveClientPolicy;
    const cases: readonly [Partial<ClientPolicyChange>, string, string][] = [
      [{ expectedVersion: -1 }, "expectedVersion", "invalid_revision"],
      [{ expectedVersion: 1.5 }, "expectedVersion", "invalid_revision"],
      [{ expectedVersion: 10_000 }, "expectedVersion", "invalid_revision"],
      [{ disabledModules: ["PEOPLE", "PEOPLE"] }, "disabledModules", "invalid_module"],
      [{ disabledModules: ["OTHER"] as never }, "disabledModules", "invalid_module"],
      [
        { minimumBuilds: { android: -1, ios: 0, web: 0 } },
        "minimumBuilds.android",
        "invalid_build_number",
      ],
      [
        { minimumBuilds: { android: 0, ios: 1.5, web: 0 } },
        "minimumBuilds.ios",
        "invalid_build_number",
      ],
      [
        { minimumBuilds: { android: 0, ios: 0, web: 1_000_000_000 } },
        "minimumBuilds.web",
        "invalid_build_number",
      ],
      [
        { minimumBuilds: { android: Number.NaN, ios: 0, web: 0 } },
        "minimumBuilds.android",
        "invalid_build_number",
      ],
      [{ activateAt: "2026-02-30T00:00:00Z" }, "activateAt", "invalid_timestamp"],
      [{ activateAt: "2026-10-10T10:00:00+07:00" }, "activateAt", "invalid_timestamp"],
      [{ activateAt: "2023-12-31T23:59:59.999999Z" }, "activateAt", "invalid_timestamp"],
      [{ activateAt: "2101-01-01T00:00:00Z" }, "activateAt", "invalid_timestamp"],
      [{ reason: " " }, "reason", "invalid_reason"],
      [{ reason: "x".repeat(1001) }, "reason", "invalid_reason"],
      [{ reason: "First\nSecond" }, "reason", "invalid_reason"],
      [{ reason: `First${String.fromCharCode(0x85)}Second` }, "reason", "invalid_reason"],
    ];
    for (const [change, field, code] of cases)
      expect(await save.execute(access, operation, input(change), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_client_policy", fields: { [field]: code } },
      });
    expect(
      await save.execute(access, operation, input({ expectedVersion: 9999 }), signal()),
    ).toMatchObject({ ok: false, failure: { code: "client_policy_revision_limit" } });
    expect(request).not.toHaveBeenCalled();
  });

  it("compares the maintenance interval at server precision and delegates activation freshness to the server", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id: companyId, version: 0 });
    const save = createAdministrationFeature({ request }).saveClientPolicy;
    for (const endsAt of [
      "2026-10-10T00:00:00Z",
      "2026-10-09T23:59:59Z",
      "2026-10-17T00:00:00.000001Z",
    ])
      expect(
        await save.execute(
          access,
          operation,
          input({
            maintenance: {
              startsAt: "2026-10-10T00:00:00Z",
              endsAt,
            },
          }),
          signal(),
        ),
      ).toMatchObject({
        ok: false,
        failure: {
          code: "invalid_client_policy",
          fields: { maintenance: "invalid_maintenance_window" },
        },
      });
    expect(request).not.toHaveBeenCalled();
    expect(
      await save.execute(
        access,
        operation,
        input({
          activateAt: "2024-01-01T00:00:00Z",
          maintenance: {
            startsAt: "2026-10-10T00:00:00Z",
            endsAt: "2026-10-17T00:00:00.000000999Z",
          },
        }),
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { version: 0 } });
    expect(request).toHaveBeenCalledOnce();
  });

  it("sends one complete whitelisted replacement and identifies the company receipt independently of client builds", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: companyId, version: 8, internal: "PRIVATE" });
    const feature = createAdministrationFeature({ request });
    const proposed = {
      ...input({
        expectedVersion: 7,
        activateAt: "2026-11-01T00:00:00.123456Z",
        disabledModules: ["REPORTING", "PAYROLL"],
        reason: "  Mobile build support  ",
        minimumBuilds: { android: 42, ios: 19, web: 0 },
        maintenance: { startsAt: "2026-11-01T01:00:00Z", endsAt: "2026-11-01T01:30:00Z" },
      }),
      actorId: "PRIVATE",
      companyId: "WRONG",
    };
    const result = await feature.saveClientPolicy.execute(access, operation, proposed, signal());
    expect(result).toEqual({ ok: true, value: { id: companyId, version: 8 } });
    if (!result.ok) throw new Error("Expected a policy receipt");
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(request.mock.lastCall?.[0]).toEqual({
      path,
      method: "PUT",
      operationId: operation,
      body: {
        expectedVersion: 7,
        activateAt: "2026-11-01T00:00:00.123456Z",
        disabledModules: ["PAYROLL", "REPORTING"],
        minimumBuilds: { android: 42, ios: 19, web: 0 },
        maintenance: { startsAt: "2026-11-01T01:00:00Z", endsAt: "2026-11-01T01:30:00Z" },
        reason: "Mobile build support",
      },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    request.mockResolvedValueOnce({ id: companyId, version: 9999 });
    expect(
      await feature.saveClientPolicy.execute(
        access,
        operation,
        input({ expectedVersion: 9998 }),
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { version: 9999 } });
  });

  it("keeps invalid or mismatched acknowledgements uncertain instead of retrying or substituting a revision", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const save = createAdministrationFeature({ request }).saveClientPolicy;
    for (const receipt of [
      { id: operation, version: 0 },
      { id: companyId, version: 1 },
      { id: companyId, version: 0.5 },
      { id: companyId, version: -1 },
      {},
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(await save.execute(access, operation, input(), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    expect(request).toHaveBeenCalledTimes(5);
  });

  it("preserves stable field failures and cancellation without leaking diagnostics or retrying", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
      new HttpResponseError(422, {
        code: "invalid_client_policy",
        fields: { reason: "invalid_reason" },
        detail: "PRIVATE DATABASE DETAIL",
        parameters: {},
      }),
    );
    const save = createAdministrationFeature({ request }).saveClientPolicy;
    const result = await save.execute(access, operation, input(), signal());
    expect(result).toMatchObject({
      ok: false,
      failure: {
        code: "invalid_client_policy",
        fields: { reason: "invalid_reason" },
      },
    });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    let resolve!: (value: unknown) => void;
    request.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const abort = new AbortController();
    const pending = save.execute(access, operation, input(), abort.signal);
    abort.abort();
    resolve({ id: companyId, version: 0 });
    await expect(pending).rejects.toThrow();
    expect(request).toHaveBeenCalledTimes(2);
    expect(request.mock.lastCall?.[1]).toBe(abort.signal);
  });
});
