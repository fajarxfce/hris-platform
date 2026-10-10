import { expect, type Page } from "@playwright/test";
import type { LeavePolicyChangeDto } from "../src/features/leave/data/models/leave-policy-change-dto";
import type { LeavePolicyReviewDto } from "../src/features/leave/data/models/leave-type-dto";
import { installLeavePoliciesApi } from "./leave-policies-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installLeavePolicyEditorApi(page: Page) {
  const api = await installLeavePoliciesApi(page);
  const writes: { company: string; id: string; operation: string; body: LeavePolicyChangeDto }[] =
    [];
  const receipts = new Map<string, { id: string; version: number; payload: string }>();
  let rejected: string | null = null;
  let dropped = false;
  let held: { entered: ReturnType<typeof gate>; released: ReturnType<typeof gate> } | null = null;
  function saveRecord(company: string, id: string, body: LeavePolicyChangeDto) {
    const records = api.records.get(company) ?? [];
    const old = records.find((record) => record.current.id === id);
    const version = (old?.current.version ?? -1) + 1;
    const { expectedVersion: _, reason, code, ...terms } = body;
    const record: LeavePolicyReviewDto = {
      current: {
        ...terms,
        code,
        id,
        version,
        appliedRevision: version,
        allowedContracts: [...terms.allowedContracts],
      },
      history: {
        items: [
          {
            ...terms,
            allowedContracts: [...terms.allowedContracts],
            revision: version,
            reason,
            actorId: "a9000000-0000-4000-8000-000000000001",
            recordedAt: "2026-10-10T03:00:00Z",
          },
          ...(old?.history.items ?? []),
        ],
        nextCursor: null,
      },
    };
    api.records.set(
      company,
      [...records.filter((value) => value.current.id !== id), record].sort((left, right) =>
        left.current.code.localeCompare(right.current.code),
      ),
    );
    return { id, version };
  }
  await page.route("**/api/v1/companies/*/leave/types/*", async (route) => {
    expect(route.request().method()).toBe("PUT");
    const path = new URL(route.request().url()).pathname.split("/");
    const company = path[4] ?? "";
    const id = path[7] ?? "";
    const operation = route.request().headers()["idempotency-key"] ?? "";
    expect(operation).toMatch(/^[0-9a-f-]{36}$/u);
    const body = route.request().postDataJSON() as LeavePolicyChangeDto;
    writes.push({ company, id, operation, body });
    const hold = held;
    if (hold) {
      held = null;
      hold.entered.resolve();
      await hold.released.promise;
    }
    if (rejected) {
      const code = rejected;
      rejected = null;
      return route.fulfill({ status: code === "stale_version" ? 409 : 403, json: { code } });
    }
    const previous = receipts.get(`${company}:${operation}`);
    if (previous) {
      expect(JSON.stringify(body)).toBe(previous.payload);
      return route.fulfill({ json: { id: previous.id, version: previous.version } });
    }
    const current = api.records.get(company)?.find((record) => record.current.id === id);
    if ((current?.current.version ?? null) !== body.expectedVersion)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    const receipt = saveRecord(company, id, body);
    receipts.set(`${company}:${operation}`, { ...receipt, payload: JSON.stringify(body) });
    if (dropped) {
      dropped = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...api,
    writes,
    dropNext: () => {
      dropped = true;
    },
    rejectNext: (code: string) => {
      rejected = code;
    },
    advance: (company: string, id: string) => {
      const policy = api.records.get(company)?.find((record) => record.current.id === id)?.current;
      if (!policy) throw new Error("Missing fixture policy");
      saveRecord(company, id, {
        ...policy,
        name: "Other writer policy",
        expectedVersion: policy.version,
        reason: "Independent update",
      });
    },
    holdSave: () => {
      const entered = gate();
      const released = gate();
      held = { entered, released };
      return { entered: entered.promise, release: released.resolve };
    },
  };
}
