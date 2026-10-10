import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { LifecycleCaseStart } from "../domain/entities/lifecycle-case-start";
import { createLifecycleFeature } from "./lifecycle-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["people.lifecycle.manage"] };
const id = (n = 1) => `a0000000-abcd-4000-8000-${String(n).padStart(12, "0")}`;
const operation = id(4) as OperationId;
const input: LifecycleCaseStart = {
  id: id(),
  employmentId: id(2),
  templateId: id(3),
  templateVersion: 0,
  targetDate: "2026-10-01",
  assignees: {},
  reason: "Start onboarding",
};
const signal = () => new AbortController().signal;

describe("lifecycle case start boundary", () => {
  it("checks the manage grant and finite command values before any I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const action = createLifecycleFeature({ request }).startCase;
    for (const permissions of [
      [],
      ["people.read"],
      ["people.manage"],
      ["people.lifecycle.read"],
      ["people.lifecycle.perform"],
    ])
      expect(
        await action.execute({ companyId, permissions }, operation, input, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const invalid of [
      { ...input, id: "../case" },
      { ...input, employmentId: "" },
      { ...input, templateId: "../template" },
      { ...input, templateVersion: -1 },
      { ...input, templateVersion: 0.5 },
      { ...input, templateVersion: Number.MAX_SAFE_INTEGER + 1 },
      { ...input, targetDate: "2026-02-29" },
      { ...input, targetDate: "1899-12-31" },
      { ...input, targetDate: "2201-01-01" },
      { ...input, reason: " " },
      { ...input, reason: "x".repeat(1001) },
      { ...input, assignees: { "bad/key": id(5) } },
      { ...input, assignees: { equipment: "../account" } },
      {
        ...input,
        assignees: Object.fromEntries(Array.from({ length: 65 }, (_, n) => [`task_${n}`, id(5)])),
      },
    ])
      expect(await action.execute(access, operation, invalid, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_lifecycle_case" },
      });
    expect(await action.execute(access, "invalid" as OperationId, input, signal())).toMatchObject({
      ok: false,
    });
    expect(request).not.toHaveBeenCalled();
  });
  it("sends the selected template version and maps only a matching initial receipt", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ id: id().toUpperCase(), version: 0, private: "PRIVATE" });
    const action = createLifecycleFeature({ request }).startCase;
    const result = await action.execute(
      access,
      operation,
      {
        ...input,
        id: id().toUpperCase(),
        employmentId: id(2).toUpperCase(),
        templateId: id(3).toUpperCase(),
        assignees: { equipment: id(5).toUpperCase() },
        reason: " Start onboarding ",
        private: "PRIVATE",
      } as LifecycleCaseStart,
      signal(),
    );
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/lifecycle/cases`,
      method: "POST",
      operationId: operation,
      body: { ...input, assignees: { equipment: id(5) } },
    });
    expect(result).toEqual({ ok: true, value: { id: id(), version: 0 } });
    if (result.ok) expect(Object.isFrozen(result.value)).toBe(true);
    for (const receipt of [
      { id: id(2), version: 0 },
      { id: id(), version: 1 },
      { id: id(), version: -1 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(await action.execute(access, operation, input, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });
  it.each(["stale_template_version", "employment_unavailable", "data_conflict"])(
    "preserves %s without retrying or exposing technical text",
    async (code) => {
      const request = vi
        .fn<HttpClient["request"]>()
        .mockRejectedValue(
          new HttpResponseError(409, { code, detail: "PRIVATE DATABASE DETAILS" }),
        );
      const result = await createLifecycleFeature({ request }).startCase.execute(
        access,
        operation,
        input,
        signal(),
      );
      expect(result).toMatchObject({ ok: false, failure: { code } });
      expect(JSON.stringify(result)).not.toContain("PRIVATE");
      expect(request).toHaveBeenCalledOnce();
    },
  );
  it.each(["success", "failure"] as const)(
    "propagates cancellation and discards a late %s",
    async (outcome) => {
      let resolve!: (value: unknown) => void;
      let reject!: (value: unknown) => void;
      const request = vi.fn<HttpClient["request"]>().mockImplementation(
        () =>
          new Promise((yes, no) => {
            resolve = yes;
            reject = no;
          }),
      );
      const action = createLifecycleFeature({ request }).startCase;
      const aborted = new AbortController();
      aborted.abort();
      expect(() => action.execute(access, operation, input, aborted.signal)).toThrow();
      expect(request).not.toHaveBeenCalled();
      const owner = new AbortController();
      const pending = action.execute(access, operation, input, owner.signal);
      owner.abort();
      if (outcome === "success") resolve({ id: id(), version: 0 });
      else reject(new Error("Late transport failure"));
      await expect(pending).rejects.toMatchObject({ name: "AbortError" });
    },
  );
});
