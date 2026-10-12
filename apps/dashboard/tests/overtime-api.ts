import { expect, type Page } from "@playwright/test";
import type { OvertimeDetailsDto } from "../src/features/workforce/data/models/overtime-details-dto";
import type { OvertimeRequestDto } from "../src/features/workforce/data/models/overtime-request-dto";
import {
  identifier,
  overtimeDetailsDtoFixture,
  overtimeDtoFixture,
} from "../src/features/workforce/di/overtime-fixture";
import { companyIds, installIdentityApi } from "./identity-api";

export const overtimeRecordId = (company = 0, number = 1) =>
  identifier(10_000 + company * 1000 + number);

function record(company: number, number: number): OvertimeRequestDto {
  const value = overtimeDtoFixture(
    number % 2 === 0 ? "APPROVED" : "PENDING",
    number === 1 ? 52 : undefined,
  );
  return {
    ...value,
    id: overtimeRecordId(company, number),
    employeeId: identifier(20_000 + company * 1000 + number),
    employeeNumber: `${company === 0 ? "NORTH" : "SOUTH"}-${String(number).padStart(3, "0")}`,
    employeeName: `${company === 0 ? "North" : "South"} Employee ${number}`,
    approvalId: identifier(30_000 + company * 1000 + number),
    reason: `Inventory count ${number}`,
  };
}

function details(request: OvertimeRequestDto, after: string | null): OvertimeDetailsDto {
  const value = overtimeDetailsDtoFixture(request.status, request.version, after);
  return {
    ...value,
    request,
    approval:
      value.approval && request.approvalId ? { ...value.approval, id: request.approvalId } : null,
    history: {
      ...value.history,
      items: value.history.items.map((change) => ({
        ...change,
        approvalId: change.revision === 0 ? null : request.approvalId,
        reason: change.revision === 0 ? request.reason : change.reason,
      })),
    },
  };
}

function gate() {
  let release!: () => void, enter!: () => void;
  return {
    held: new Promise<void>((done) => {
      release = done;
    }),
    started: new Promise<void>((done) => {
      enter = done;
    }),
    release: () => release(),
    enter: () => enter(),
  };
}

/** Transport fixture for production adapters/controllers; API integration owns business transitions. */
export async function installOvertimeApi(
  page: Page,
  permissions = ["overtime.read", "approvals.read"],
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions,
  });
  const records = new Map<string, OvertimeRequestDto[]>(
    companyIds.map((company, index) => [
      company,
      Array.from({ length: index === 0 ? 53 : 1 }, (_, number) => record(index, number + 1)),
    ]),
  );
  const reads: URL[] = [];
  let rejected: string | null = null,
    malformed = false;
  let waiting: { kind: "list" | "details"; gate: ReturnType<typeof gate> } | null = null;
  await page.route("**/api/v1/companies/*/workforce/overtime**", async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    const url = new URL(route.request().url()),
      parts = url.pathname.split("/");
    reads.push(url);
    const all = records.get(parts[4] ?? "") ?? [],
      id = parts[7],
      kind = id ? "details" : "list";
    const failure = rejected,
      invalid = malformed,
      held = waiting?.kind === kind ? waiting.gate : null;
    rejected = null;
    malformed = false;
    if (held) waiting = null;
    let response: object | null;
    if (id) {
      expect(url.searchParams.get("historyLimit")).toBe("50");
      const request = all.find((item) => item.id === id);
      const value = request ? details(request, url.searchParams.get("historyAfter")) : null;
      response =
        invalid && value
          ? {
              ...value,
              request: {
                ...value.request,
                requested: { ...value.request.requested, workedMinutes: -1 },
              },
            }
          : value;
    } else {
      expect(url.searchParams.get("limit")).toBe("50");
      const from = url.searchParams.get("from") ?? "",
        until = url.searchParams.get("until") ?? "";
      const after = url.searchParams.get("after"),
        status = url.searchParams.get("status"),
        employee = url.searchParams.get("employeeId");
      const filtered = all.filter(
        (item) =>
          item.workDate >= from &&
          item.workDate <= until &&
          (after === null || item.id > after) &&
          (status === null || item.status === status) &&
          (employee === null || item.employeeId === employee),
      );
      const items = filtered.slice(0, 50);
      response = {
        items: invalid && items.length ? [items[0], items[0]] : items,
        nextCursor: filtered.length > 50 ? items.at(-1)?.id : null,
      };
    }
    const snapshot = structuredClone(response);
    if (held) {
      held.enter();
      await held.held;
    }
    return route.fulfill(
      failure
        ? { status: 403, json: { code: failure, detail: "PRIVATE TECHNICAL ERROR" } }
        : snapshot
          ? { json: snapshot }
          : { status: 404, json: { code: "overtime_request_not_found" } },
    );
  });
  await page.route("**/api/v1/companies/*/approvals**", async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    const url = new URL(route.request().url()),
      parts = url.pathname.split("/");
    const values = (records.get(parts[4] ?? "") ?? []).flatMap((request) => {
      const workflow = details(request, null).approval;
      return workflow
        ? [
            {
              ...workflow,
              kind: "OVERTIME",
              resourceId: request.id,
              authorId: request.authorId,
              requesterId: request.requesterAccountId,
              templateId: identifier(40_001),
              templateRevision: 0,
              stages: workflow.stages.map((assignees) => ({ assignees })),
              submittedAt: request.submittedAt,
              excludedAccountIds: [request.authorId],
            },
          ]
        : [];
    });
    if (parts[6]) {
      const value = values.find((item) => item.id === parts[6]);
      return route.fulfill(
        value ? { json: value } : { status: 404, json: { code: "approval_not_found" } },
      );
    }
    const after = url.searchParams.get("after"),
      pending = values.filter(
        (item) => item.status === "PENDING" && (after === null || item.id > after),
      );
    return route.fulfill({
      json: {
        items: pending.slice(0, 20),
        nextCursor: pending.length > 20 ? pending[19]?.id : null,
      },
    });
  });
  return {
    identity,
    reads,
    records,
    rejectNext: (code: string) => {
      rejected = code;
    },
    malformNext: () => {
      malformed = true;
    },
    holdRead: (kind: "list" | "details") => {
      const next = gate();
      waiting = { kind, gate: next };
      return next;
    },
  };
}
