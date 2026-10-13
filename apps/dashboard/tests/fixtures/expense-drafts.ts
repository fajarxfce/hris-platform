import type { ExpenseClaimReviewDto } from "../../src/features/expenses/data/models/expense-claim-dto";
import type { ExpenseDraftChangeDto } from "../../src/features/expenses/data/models/expense-draft-change-dto";
import type { ExpenseDraftReferencesDto } from "../../src/features/expenses/data/models/expense-drafting-dto";
import { expenseClaimRecord, expenseIdentifier } from "./expense-claims";
import { expensePolicyRecord } from "./expense-policies";

export function expenseDraftEmployees(company = 0, count = 2, start = 0) {
  return {
    companyDate: "2026-10-13",
    timezone: "Asia/Jakarta",
    employees: {
      items: Array.from({ length: count }, (_, index) => ({
        id: expenseIdentifier(100 + start + index, company),
        employeeNumber: `EMP-${String(start + index + 1).padStart(3, "0")}`,
        legalName: `${company ? "South" : "North"} Employee ${start + index + 1}`,
        status: "ACTIVE" as const,
        version: 0,
      })),
      nextCursor: count === 20 ? `EMP-${String(start + count).padStart(3, "0")}` : null,
    },
  };
}
export function expenseDraftReceipts(company = 0, count = 2) {
  return {
    items: Array.from({ length: count }, (_, index) => ({
      documentId: expenseIdentifier(5000 + index, company),
      revisionId: expenseIdentifier(6001 + index, company),
      revisionNumber: 1,
      title: `Travel receipt ${index + 1}`,
      fileName: `receipt-${index + 1}.pdf`,
      mediaType: "application/pdf" as const,
      size: 128,
      validatedAt: "2026-10-12T08:00:00Z",
    })),
    nextCursor: count === 20 ? expenseIdentifier(5000 + count - 1, company) : null,
  };
}
export function expenseDraftCategories(company = 0) {
  return {
    items: [
      {
        ...expensePolicyRecord(company).current,
        id: expenseIdentifier(400, company),
        code: "TRAVEL",
        name: "Business travel",
        active: true,
        effectiveFrom: "2026-01-01",
        version: 3,
        appliedRevision: 1,
      },
    ],
    nextCursor: null,
  };
}
export function expenseDraftCostCenters(company = 0) {
  return {
    items: [{ id: expenseIdentifier(500, company), code: "OPS", name: "Operations", version: 1 }],
    nextCursor: null,
  };
}
export function expenseDraftReferences(company = 0): ExpenseDraftReferencesDto {
  return {
    employmentId: expenseIdentifier(100, company),
    categories: [
      {
        id: expenseIdentifier(400, company),
        code: "TRAVEL",
        name: "Business travel",
        active: true,
      },
    ],
    costCenters: [
      { id: expenseIdentifier(500, company), code: "OPS", name: "Operations", active: true },
    ],
    receipts: [
      {
        revisionId: expenseIdentifier(6001, company),
        documentId: expenseIdentifier(5000, company),
        fileName: "receipt-1.pdf",
        mediaType: "application/pdf",
        size: 128,
        status: "READY",
      },
    ],
  };
}
export function expenseDraftRecord(company = 0, returned = false): ExpenseClaimReviewDto {
  const record = expenseClaimRecord(company, 1, returned ? 2 : 0),
    claim = record.claim;
  claim.status = returned ? "RETURNED" : "DRAFT";
  claim.draftRevision = 0;
  claim.draft.revision = 0;
  claim.draft.employeeName = `${company ? "South" : "North"} Employee 1`;
  claim.draft.employeeNumber = "EMP-001";
  if (!returned) {
    claim.submissionCount = 0;
    claim.latestSubmissionId = null;
    record.submissions.items = [];
  }
  record.submissions.items = record.submissions.items.map((item) => ({
    ...item,
    draftRevision: 0,
  }));
  record.history.items = record.history.items.map((item) => ({
    ...item,
    draftRevision: 0,
    status: item.version === 2 ? "RETURNED" : item.version === 1 ? "PENDING" : "DRAFT",
    kind: item.version === 2 ? "REVIEWED" : item.version === 1 ? "SUBMITTED" : "DRAFT_SAVED",
    submissionId: item.version > 0 ? claim.latestSubmissionId : null,
  }));
  return record;
}
export function expenseDraftInput(company = 0): ExpenseDraftChangeDto {
  const claim = expenseDraftRecord(company).claim;
  return {
    employmentId: claim.employmentId,
    expectedVersion: null,
    title: "Travel to client office",
    description: "October visit",
    lines: claim.draft.lines,
    reason: "Travel details recorded",
  };
}
