import { expect, type Page } from "@playwright/test";
import type { LeaveRequestPreviewDto } from "../src/features/leave/data/models/leave-request-preview-dto";
import type {
  LeaveRequestSelectionDto,
  LeaveRequestSubmissionDto,
} from "../src/features/leave/data/models/leave-request-submission-dto";
import {
  preparationEvidenceDto,
  preparationPreviewDto,
  preparationTypesDto,
} from "../src/features/leave/di/leave-preparation-fixture";
import { leaveRecord } from "./fixtures/leave";
import { installLeaveApi } from "./leave-api";

type RequestKind = "employees" | "types" | "evidence" | "preview" | "submit";
function gate() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

/** Owns transport responses while exercising the production form, DI and error boundaries. */
export async function installLeaveCreationApi(
  page: Page,
  permissions = ["leave.manage", "leave.read"],
) {
  const base = await installLeaveApi(page, permissions);
  const reads: URL[] = [];
  const previews: LeaveRequestSelectionDto[] = [];
  const writes: { company: string; operation: string; body: LeaveRequestSubmissionDto }[] = [];
  const receipts = new Map<string, { payload: string; id: string }>();
  const evidence = preparationEvidenceDto();
  let requireEvidence = false;
  let lose = false;
  let commits = 0;
  let reject: { kind: RequestKind; code: string; status: number } | null = null;
  let held: {
    kind: RequestKind;
    entered: ReturnType<typeof gate>;
    release: ReturnType<typeof gate>;
  } | null = null;

  function preview(selection: LeaveRequestSelectionDto): LeaveRequestPreviewDto {
    const original = preparationPreviewDto();
    const template = original.days[0];
    if (!template) throw new Error("Missing fixture schedule");
    const days = selection.days
      .filter((day) => day.workDate !== "2026-10-06")
      .map<LeaveRequestPreviewDto["days"][number]>((day) => ({
        ...template,
        ...day,
        startsAt: `${day.workDate}T15:00:00Z`,
        endsAt: `${day.workDate}T23:00:00Z`,
        chargedDays: day.portion === "FULL" ? "1" : "0.5",
        chargedMinutes: day.portion === "FULL" ? 450 : 225,
      }));
    const annual = new Map<number, number>();
    for (const day of days) {
      const year = Number(day.workDate.slice(0, 4));
      annual.set(year, (annual.get(year) ?? 0) + Number(day.chargedDays));
    }
    return {
      ...original,
      employeeId: selection.employeeId,
      policy: { ...original.policy, typeId: selection.typeId, attachmentRequired: requireEvidence },
      days,
      chargedDays: String(days.reduce((sum, day) => sum + Number(day.chargedDays), 0)),
      excludedDates: selection.days
        .filter((day) => day.workDate === "2026-10-06")
        .map((day) => day.workDate),
      balances: [...annual].map(([year, requested]) => ({
        year,
        availableDays: "12.5",
        reservedDays: "1",
        consumedDays: "1",
        requestedDays: String(requested),
        remainingDays: String(12.5 - requested),
        version: 4,
      })),
    };
  }
  await page.route("**/api/v1/companies/*/leave/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const parts = url.pathname.split("/");
    const company = parts[4] ?? "";
    let kind: RequestKind;
    if (parts[6] === "types" && request.method() === "GET") kind = "types";
    else if (parts[6] === "preparation" && request.method() === "GET")
      kind = parts[9] === "evidence" ? "evidence" : "employees";
    else if (parts[6] === "requests" && request.method() === "POST")
      kind = parts[7] === "preview" ? "preview" : "submit";
    else return route.fallback();

    const body = request.method() === "POST" ? request.postDataJSON() : null;
    const operation = request.headers()["idempotency-key"] ?? "";
    if (kind === "preview") previews.push(body);
    else if (kind === "submit") {
      expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
      expect(body).not.toHaveProperty("companyId");
      writes.push({ company, operation, body });
    } else reads.push(url);
    const wait = held;
    if (wait?.kind === kind) {
      held = null;
      wait.entered.resolve();
      await wait.release.promise;
    }
    const fail = (code: string, status = 409) =>
      route.fulfill({
        status,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE FAILURE" },
      });
    if (reject?.kind === kind) {
      const failure = reject;
      reject = null;
      return fail(failure.code, failure.status);
    }
    if (kind === "employees") {
      expect(url.searchParams.get("limit")).toBe("20");
      const query = url.searchParams.get("query")?.toLowerCase() ?? "";
      const after = url.searchParams.get("after") ?? "";
      const available = (base.records.get(company) ?? [])
        .map((record) => ({
          id: record.employeeId,
          employeeNumber: record.employeeNumber,
          legalName: record.employeeName,
          status: "ACTIVE",
          version: 0,
        }))
        .sort((left, right) => left.employeeNumber.localeCompare(right.employeeNumber))
        .filter(
          (item) =>
            item.employeeNumber > after &&
            `${item.legalName} ${item.employeeNumber}`.toLowerCase().includes(query),
        );
      const items = available.slice(0, 20);
      return route.fulfill({
        json: {
          companyDate: "2026-10-01",
          timezone: "Asia/Jakarta",
          employees: {
            items,
            nextCursor: available.length > 20 ? items.at(-1)?.employeeNumber : null,
          },
        },
      });
    }
    if (kind === "types") {
      expect(url.searchParams.get("limit")).toBe("20");
      expect(url.searchParams.get("asOf")).toMatch(/^\d{4}-\d{2}-\d{2}$/u);
      const types = preparationTypesDto();
      return route.fulfill({
        json: {
          ...types,
          items: types.items.map((type) => ({ ...type, attachmentRequired: requireEvidence })),
        },
      });
    }
    if (kind === "evidence") {
      expect(url.searchParams.get("limit")).toBe("20");
      return route.fulfill({ json: evidence });
    }
    if (kind === "preview") return route.fulfill({ json: preview(body) });

    const key = `${company}:${operation}`;
    const payload = JSON.stringify(body);
    const receipt = receipts.get(key);
    if (receipt)
      return receipt.payload === payload
        ? route.fulfill({ json: { id: receipt.id, version: 0 } })
        : fail("operation_payload_mismatch");
    if (requireEvidence && body.attachmentRevisionIds.length === 0)
      return fail("leave_attachment_required", 422);
    const reviewed = preview(body);
    const employee = base.records.get(company)?.find((item) => item.employeeId === body.employeeId);
    if (!employee) return fail("employee_not_found", 404);
    const record = leaveRecord();
    const created = {
      ...record,
      id: body.id,
      employeeId: employee.employeeId,
      employeeName: employee.employeeName,
      employeeNumber: employee.employeeNumber,
      reason: body.reason,
      policy: reviewed.policy,
      days: reviewed.days,
      chargedDays: reviewed.chargedDays,
      history: {
        ...record.history,
        items: record.history.items.map((item) => ({ ...item, reason: body.reason })),
      },
      attachments: evidence.items
        .filter((item) => body.attachmentRevisionIds.includes(item.revisionId))
        .map((item) => ({ ...item, sha256: "a".repeat(64) })),
    };
    base.records.get(company)?.unshift(created);
    receipts.set(key, { payload, id: body.id });
    commits += 1;
    if (lose) {
      lose = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: { id: body.id, version: 0 } });
  });
  return {
    ...base,
    preparationReads: reads,
    previews,
    writes,
    get commits() {
      return commits;
    },
    requireEvidence: () => {
      requireEvidence = true;
    },
    loseNext: () => {
      lose = true;
    },
    rejectNext: (kind: RequestKind, code: string, status = 409) => {
      reject = { kind, code, status };
    },
    holdPreparation: (kind: RequestKind) => {
      const entered = gate();
      const release = gate();
      held = { kind, entered, release };
      return { entered: entered.promise, release: release.resolve };
    },
  };
}
