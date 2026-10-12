import { expect, type Page } from "@playwright/test";
import type { OvertimeDetailsDto } from "../src/features/workforce/data/models/overtime-details-dto";
import {
  identifier,
  overtimeDetailsDtoFixture,
} from "../src/features/workforce/di/overtime-fixture";
import { companyIds } from "./identity-api";
import { installOvertimeApi, overtimeRecordId } from "./overtime-api";

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
type Body = {
  expectedVersion: number;
  reason: string;
  decision?: "APPROVE" | "REJECT";
  actual?: { startsAt: string; endsAt: string; breakMinutes: number };
};

/** Transport outcomes for production UI code; real API tests verify business transitions separately. */
export async function installOvertimeActionsApi(
  page: Page,
  planned = false,
  permissions = ["overtime.read", "overtime.manage", "overtime.approve", "approvals.read"],
) {
  const base = await installOvertimeApi(page, permissions);
  const record = overtimeDetailsDtoFixture(planned ? "PLANNED" : "PENDING");
  record.request.id = overtimeRecordId();
  record.request.employeeName = "North Employee 1";
  record.request.employeeNumber = "NORTH-001";
  if (!planned) record.actions.push("WITHDRAW");
  const records = base.records.get(companyIds[0]);
  if (!records) throw new Error("Missing company fixture");
  records[0] = record.request;
  const writes: { company: string; id: string; endpoint: string; operation: string; body: Body }[] =
    [];
  const receipts = new Map<string, { payload: string; version: number }>();
  let rejected: { code: string; status: number } | null = null,
    readFailure: string | null = null;
  let lose = false,
    commits = 0;
  let heldWrite: ReturnType<typeof gate> | null = null,
    heldRead: ReturnType<typeof gate> | null = null;
  function appendChange(
    kind: OvertimeDetailsDto["history"]["items"][number]["kind"],
    reason: string,
  ) {
    const request = record.request;
    record.history.items.unshift({
      revision: request.version,
      kind,
      status: request.status,
      actual: request.actual ? { ...request.actual } : null,
      approvedMinutes: request.approvedMinutes,
      approvalId: request.approvalId,
      actorId: identifier(22),
      recordedAt: "2026-10-13T12:00:00Z",
      reason,
    });
  }
  await page.route(
    `**/api/v1/companies/${companyIds[0]}/workforce/overtime/${record.request.id}**`,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url());
      if (request.method() === "GET") {
        base.reads.push(url);
        expect(url.searchParams.get("historyLimit")).toBe("50");
        const snapshot = structuredClone(record),
          failure = readFailure;
        readFailure = null;
        const after = url.searchParams.get("historyAfter");
        if (after !== null)
          snapshot.history.items = snapshot.history.items.filter(
            (item) => item.revision < Number(after),
          );
        if (heldRead) {
          const held = heldRead;
          heldRead = null;
          held.enter();
          await held.held;
        }
        return route.fulfill(
          failure
            ? { status: 403, json: { code: failure, detail: "PRIVATE DETAIL" } }
            : { json: snapshot },
        );
      }
      if (request.method() !== "POST") return route.fallback();
      const endpoint = url.pathname.split("/")[8] ?? "";
      const operation = request.headers()["idempotency-key"] ?? "";
      const body = request.postDataJSON() as Body;
      writes.push({ company: companyIds[0], id: record.request.id, endpoint, operation, body });
      expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
      if (heldWrite) {
        const held = heldWrite;
        heldWrite = null;
        held.enter();
        await held.held;
      }
      const failure = (code: string, status = 409) =>
        route.fulfill({
          status,
          json: { code, detail: "PRIVATE DETAIL", fields: {}, parameters: {} },
        });
      if (rejected) {
        const value = rejected;
        rejected = null;
        return failure(value.code, value.status);
      }
      const payload = JSON.stringify({ endpoint, body }),
        previous = receipts.get(operation);
      if (previous)
        return previous.payload === payload
          ? route.fulfill({ json: { id: record.request.id, version: previous.version } })
          : failure("operation_payload_mismatch");
      const current = record.request;
      if (body.expectedVersion !== current.version) return failure("stale_version");
      if (endpoint === "actual") {
        expect(current.status).toBe("PLANNED");
        if (!body.actual) throw new Error("Expected actual time");
        current.actual = {
          ...body.actual,
          workedMinutes:
            (Date.parse(body.actual.endsAt) - Date.parse(body.actual.startsAt)) / 60_000 -
            body.actual.breakMinutes,
        };
        current.submittedBy = current.authorId;
        current.submittedAt = "2026-10-13T12:00:00Z";
        current.approvalId = identifier(702);
        current.status = "PENDING";
        record.approval = {
          id: current.approvalId,
          status: "PENDING",
          stages: [[identifier(22)]],
          currentStep: 0,
          version: 0,
        };
      } else if (endpoint === "withdraw") {
        current.status = "WITHDRAWN";
        if (record.approval) record.approval.status = "CANCELLED";
      } else {
        expect(endpoint).toBe("decisions");
        if (!record.approval) throw new Error("Expected approval");
        if (body.decision === "REJECT") {
          current.status = "REJECTED";
          record.approval.status = "REJECTED";
        } else if (record.approval.currentStep + 1 < record.approval.stages.length)
          record.approval.currentStep += 1;
        else {
          current.status = "APPROVED";
          record.approval.status = "APPROVED";
          current.approvedMinutes = current.actual?.workedMinutes ?? 0;
        }
        record.approval.version += 1;
      }
      if (["WITHDRAWN", "APPROVED", "REJECTED"].includes(current.status))
        current.decidedAt = "2026-10-13T12:00:00Z";
      current.version += 1;
      record.actions = current.status === "PENDING" ? ["APPROVE", "REJECT", "WITHDRAW"] : [];
      appendChange(
        endpoint === "actual"
          ? "ACTUAL_SUBMITTED"
          : endpoint === "withdraw"
            ? "WITHDRAWN"
            : "DECIDED",
        body.reason,
      );
      receipts.set(operation, { payload, version: current.version });
      commits += 1;
      if (lose) {
        lose = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: { id: current.id, version: current.version } });
    },
  );
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
    rejectWrite: (code: string, status = 409) => {
      rejected = { code, status };
    },
    rejectRead: (code: string) => {
      readFailure = code;
    },
    holdActionRead: () => {
      const held = gate();
      heldRead = held;
      return held;
    },
    holdWrite: () => {
      const held = gate();
      heldWrite = held;
      return held;
    },
    competingReview: () => {
      record.request.version += 1;
      appendChange("DECIDED", "Another review");
    },
  };
}
