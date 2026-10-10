import { expect, type Page } from "@playwright/test";
import type { LeaveRequestDetailsDto } from "../src/features/leave/data/models/leave-request-details-dto";
import { leaveId } from "./fixtures/leave";
import { companyIds } from "./identity-api";
import { installLeaveApi } from "./leave-api";

function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
export async function installLeaveActionsApi(
  page: Page,
  permissions = ["leave.read", "leave.approve", "leave.manage", "approvals.read"],
) {
  const base = await installLeaveApi(page, permissions);
  const record = base.records.get(companyIds[0])?.find((item) => item.id === leaveId(0, 2));
  if (!record) throw new Error("Missing fixture request");
  record.availableActions = ["DECIDE", "WITHDRAW"];
  const writes: {
    company: string;
    id: string;
    endpoint: string;
    operation: string;
    body: { version: number; reason: string; decision?: "APPROVE" | "REJECT" };
  }[] = [];
  const receipts = new Map<string, { payload: string; version: number }>();
  let reject: { code: string; status: number } | null = null;
  let lose = false;
  let commits = 0;
  let held: { entered: ReturnType<typeof gate>; release: ReturnType<typeof gate> } | null = null;
  function beginCancellation(current: LeaveRequestDetailsDto) {
    current.status = "CANCELLATION_PENDING";
    current.cancellation = {
      ...current.approval,
      id: "b6000000-0000-4000-8000-000000000002",
      currentStep: 0,
      stages: [current.approval.stages[0] ?? []],
      status: "PENDING",
      version: 0,
    };
    current.availableActions = ["DECIDE", "WITHDRAW"];
  }
  await page.route("**/api/v1/companies/*/leave/requests/*/*", async (route) => {
    const request = route.request();
    if (request.method() !== "POST") return route.fallback();
    const parts = new URL(request.url()).pathname.split("/");
    const company = parts[4] ?? "";
    const id = parts[7] ?? "";
    const endpoint = parts[8] ?? "";
    const operation = request.headers()["idempotency-key"] ?? "";
    const body = request.postDataJSON() as (typeof writes)[number]["body"];
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    writes.push({ company, id, endpoint, operation, body });
    const wait = held;
    if (wait) {
      held = null;
      wait.entered.resolve();
      await wait.release.promise;
    }
    const fail = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE FAILURE" },
      });
    if (reject) {
      const failure = reject;
      reject = null;
      return fail(failure.code, failure.status);
    }
    const key = `${company}:${operation}`;
    const payload = JSON.stringify({ id, endpoint, body });
    const receipt = receipts.get(key);
    if (receipt)
      return receipt.payload === payload
        ? route.fulfill({ json: { id, version: receipt.version } })
        : fail("operation_payload_mismatch");
    const current = base.records.get(company)?.find((item) => item.id === id);
    if (!current) return fail("leave_request_not_found", 404);
    if (body.version !== current.version) return fail("stale_version");
    const cancellation = current.status === "CANCELLATION_PENDING";
    const active = cancellation ? current.cancellation : current.approval;
    if (endpoint === "cancellation") {
      if (current.status !== "APPROVED") return fail("leave_not_approved");
      beginCancellation(current);
    } else {
      if (!active || !["PENDING", "CANCELLATION_PENDING"].includes(current.status))
        return fail("leave_not_pending");
      if (endpoint === "withdraw") {
        active.status = "CANCELLED";
        current.status = cancellation ? "APPROVED" : "CANCELLED";
      } else {
        expect(endpoint).toBe("decisions");
        if (body.decision === "REJECT") {
          active.status = "REJECTED";
          current.status = cancellation ? "APPROVED" : "REJECTED";
        } else if (active.currentStep + 1 < active.stages.length) active.currentStep += 1;
        else {
          active.status = "APPROVED";
          current.status = cancellation ? "CANCELLED" : "APPROVED";
        }
      }
      active.version += 1;
    }
    current.availableActions =
      current.status === "APPROVED"
        ? ["REQUEST_CANCELLATION"]
        : ["PENDING", "CANCELLATION_PENDING"].includes(current.status)
          ? ["DECIDE", "WITHDRAW"]
          : [];
    current.version += 1;
    current.history.items.unshift({
      version: current.version,
      status: current.status,
      kind:
        endpoint === "cancellation"
          ? "CANCELLATION_REQUESTED"
          : endpoint === "withdraw"
            ? "WITHDRAWN"
            : "DECIDED",
      cancellationApprovalId: current.cancellation?.id ?? null,
      actorId: "20000000-0000-4000-8000-000000000001",
      reason: body.reason,
      recordedAt: new Date(
        new Date(current.submittedAt).getTime() + current.version * 60_000,
      ).toISOString(),
    });
    commits += 1;
    receipts.set(key, { payload, version: current.version });
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id, version: current.version } });
  });
  return {
    ...base,
    record,
    writes,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    rejectNext: (code: string, status = 409) => {
      reject = { code, status };
    },
    approve: () => {
      record.status = "APPROVED";
      record.approval.status = "APPROVED";
      record.availableActions = ["REQUEST_CANCELLATION"];
    },
    cancel: () => beginCancellation(record),
    holdWrite: () => {
      const entered = gate();
      const release = gate();
      held = { entered, release };
      return { entered: entered.promise, release: release.resolve };
    },
  };
}
