import { expect, type Page } from "@playwright/test";
import type { ApprovalReassignmentDto } from "../src/features/approvals/data/models/approval-reassignment-dto";
import { approvalId, installApprovalsApi } from "./approvals-api";
import { companyIds } from "./identity-api";

export const reassignmentAccount = (index: number) =>
  `20000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export async function installApprovalReassignmentApi(
  page: Page,
  permissions = ["approvals.read", "approvals.manage", "leave.approve"],
) {
  const base = await installApprovalsApi(page, permissions);
  const record = base.records.get(companyIds[0])?.find((item) => item.id === approvalId(0, 2));
  if (!record) throw new Error("Missing fixture approval");
  record.requesterId = reassignmentAccount(98);
  record.excludedAccountIds = [reassignmentAccount(97)];
  record.stages = [
    { assignees: [reassignmentAccount(2)] },
    { assignees: [] },
    { assignees: [reassignmentAccount(3)] },
  ];
  const writes: {
    company: string;
    id: string;
    operation: string;
    body: ApprovalReassignmentDto;
  }[] = [];
  const assigneeReads: URL[] = [];
  const receipts = new Map<string, { body: string; id: string; version: number }>();
  let failure: { code: string; status: number } | null = null;
  let lose = false;
  let commits = 0;
  await page.route("**/api/v1/companies/*/approvals/assignees?*", async (route) => {
    const url = new URL(route.request().url());
    assigneeReads.push(url);
    expect(url.searchParams.get("limit")).toBe("10");
    expect(url.searchParams.get("kind")).toBe("LEAVE");
    const after = url.searchParams.get("after");
    const query = url.searchParams.get("query")?.toLowerCase() ?? "";
    const all = [
      ...Array.from({ length: 12 }, (_, index) => ({
        id: reassignmentAccount(index + 1),
        displayName: `Approver ${String(index + 1).padStart(2, "0")}`,
      })),
      ...[97, 98, 99].map((index) => ({
        id: reassignmentAccount(index),
        displayName: `Maker ${index}`,
      })),
    ];
    const matching = all.filter(
      (item) => (!after || item.id > after) && item.displayName.toLowerCase().includes(query),
    );
    return route.fulfill({
      json: {
        items: matching.slice(0, 10),
        nextCursor: matching.length > 10 ? matching[9]?.id : null,
      },
    });
  });
  await page.route("**/api/v1/companies/*/approvals/*/reassign", async (route) => {
    const request = route.request();
    expect(request.method()).toBe("POST");
    const parts = new URL(request.url()).pathname.split("/");
    const company = parts[4] ?? "";
    const id = parts[6] ?? "";
    const body = request.postDataJSON() as ApprovalReassignmentDto;
    const operation = request.headers()["idempotency-key"] ?? "";
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    writes.push({ company, id, operation, body });
    const fail = (code: string, status = 409) =>
      route.fulfill({ status, json: { code, fields: {}, parameters: {}, detail: "PRIVATE DATA" } });
    if (failure) {
      const rejected = failure;
      failure = null;
      return fail(rejected.code, rejected.status);
    }
    const key = `${company}:${operation}`;
    const serialized = JSON.stringify(body);
    const receipt = receipts.get(key);
    if (receipt)
      return receipt.id === id && receipt.body === serialized
        ? route.fulfill({ json: { id, version: receipt.version } })
        : fail("operation_payload_mismatch");
    const current = base.records.get(company)?.find((item) => item.id === id);
    if (!current) return fail("approval_not_found", 404);
    if (body.version !== current.version || !["PENDING", "BLOCKED"].includes(current.status))
      return fail("approval_changed");
    if (
      body.assignees.some((account) =>
        [current.authorId, current.requesterId, ...current.excludedAccountIds].includes(account),
      )
    )
      return fail("approver_unavailable", 422);
    current.stages = current.stages.map((stage, index) =>
      index === current.currentStep ? { assignees: [...body.assignees] } : stage,
    );
    current.version += 1;
    current.status = "PENDING";
    commits += 1;
    receipts.set(key, { id, version: current.version, body: serialized });
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
    assigneeReads,
    get commits() {
      return commits;
    },
    loseNext: () => {
      lose = true;
    },
    rejectNext: (code: string, status = 409) => {
      failure = { code, status };
    },
    advance: () => {
      record.version += 1;
      record.currentStep = 2;
      record.status = "PENDING";
    },
    complete: () => {
      record.version += 1;
      record.status = "APPROVED";
    },
  };
}
