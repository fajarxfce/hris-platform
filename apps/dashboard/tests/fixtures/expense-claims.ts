import type { ExpenseClaimReviewDto } from "../../src/features/expenses/data/models/expense-claim-dto";
import type { ExpenseSubmissionDto } from "../../src/features/expenses/data/models/expense-submission-dto";
import { expensePolicyRecord } from "./expense-policies";
export const expenseIdentifier = (index: number, company = 0): string =>
  `ba00000${company}-0000-4000-8000-${String(index).padStart(12, "0")}`;
export const expenseReceiptBytes = new TextEncoder().encode(
  "%PDF-1.4\nFictional travel receipt\n%%EOF\n",
);
export function expenseClaimRecord(company = 0, index = 1, version = 23): ExpenseClaimReviewDto {
  const draftRevision = Math.max(0, version - 1);
  const submission = expenseIdentifier(1000 + index, company);
  const createdAt = `2026-10-13T05:00:${String(59 - index).padStart(2, "0")}Z`;
  return {
    claim: {
      id: expenseIdentifier(index, company),
      employmentId: expenseIdentifier(100, company),
      createdBy: expenseIdentifier(200, company),
      createdAt,
      version,
      draftRevision,
      status: "PENDING",
      submissionCount: 1,
      latestSubmissionId: submission,
      draft: {
        revision: draftRevision,
        employeeNumber: "EMP-017",
        employeeName: company === 0 ? "Sample Employee" : "Other Employee",
        title: `${company === 0 ? "North" : "South"} travel reimbursement ${String(index).padStart(2, "0")}`,
        description: "Customer visit",
        totalAmount: "150000.75",
        currency: "IDR",
        lines: [
          {
            id: expenseIdentifier(3000 + index, company),
            categoryId: expenseIdentifier(400, company),
            occurredOn: "2026-10-12",
            amount: "150000.75",
            description: "Return train ticket",
            costCenterId: expenseIdentifier(500, company),
            receiptRevisionIds: [expenseIdentifier(6000 + index, company)],
          },
        ],
        actorId: expenseIdentifier(200, company),
        reason: "Travel details confirmed",
        recordedAt: "2026-10-13T05:01:00Z",
      },
    },
    history: {
      items: Array.from({ length: version + 1 }, (_, offset) => {
        const revision = version - offset;
        return {
          version: revision,
          draftRevision: Math.min(revision, draftRevision),
          status: revision === version ? ("PENDING" as const) : ("DRAFT" as const),
          kind: revision === version ? ("SUBMITTED" as const) : ("DRAFT_SAVED" as const),
          actorId: expenseIdentifier(200, company),
          reason: `Saved travel revision ${revision}`,
          recordedAt: "2026-10-13T05:01:00Z",
          submissionId: revision === version ? submission : null,
        };
      }),
      nextCursor: null,
    },
    submissions: {
      items: [
        {
          id: submission,
          number: 1,
          draftRevision,
          approvalId: expenseIdentifier(7000 + index, company),
          title: `${company === 0 ? "North" : "South"} travel reimbursement ${String(index).padStart(2, "0")}`,
          totalAmount: "150000.75",
          currency: "IDR",
          submittedBy: expenseIdentifier(200, company),
          submittedAt: "2026-10-13T05:02:00Z",
        },
      ],
      nextCursor: null,
    },
    payments: [],
  };
}
export function expenseClaimReviewPage(
  record: ExpenseClaimReviewDto,
  after: number | null = null,
): ExpenseClaimReviewDto {
  const available = record.history.items.filter((row) => after === null || row.version < after);
  const items = available.slice(0, 20);
  return {
    ...record,
    history: { items, nextCursor: available.length > 20 ? String(items.at(-1)?.version) : null },
  };
}
export function expenseSubmissionRecord(
  company = 0,
  index = 1,
  version = 23,
): ExpenseSubmissionDto {
  const record = expenseClaimRecord(company, index, version);
  const summary = record.submissions.items[0];
  if (!summary) throw new Error("Missing submission fixture");
  const { approvalId, ...fields } = summary;
  return {
    ...fields,
    claimId: record.claim.id,
    employmentId: record.claim.employmentId,
    requesterId: record.claim.createdBy,
    employeeNumber: record.claim.draft.employeeNumber,
    employeeName: record.claim.draft.employeeName,
    description: record.claim.draft.description,
    makerIds: [record.claim.createdBy],
    reason: "Reimburse customer travel",
    lines: record.claim.draft.lines.map((line) => ({
      id: line.id,
      occurredOn: line.occurredOn,
      amount: line.amount,
      description: line.description,
      category: {
        ...expensePolicyRecord(company).current,
        id: line.categoryId,
        code: "TRAVEL",
        name: "Business travel",
        effectiveFrom: "2026-01-01",
        version: 2,
        appliedRevision: 1,
        active: true,
      },
      costCenter: { id: expenseIdentifier(500, company), code: "SALES", name: "Sales", version: 2 },
      receipts: [
        {
          revisionId: line.receiptRevisionIds[0] ?? "",
          fileName: "train-receipt.pdf",
          mediaType: "application/pdf",
          size: expenseReceiptBytes.byteLength,
          sha256: "a".repeat(64),
          possibleDuplicate: false,
        },
      ],
    })),
    approval: {
      id: approvalId,
      status: "PENDING",
      version: 0,
      currentStep: 0,
      templateId: expenseIdentifier(800, company),
      templateRevision: 2,
      stages: [[expenseIdentifier(900, company)]],
    },
    claimVersion: version,
    claimStatus: "PENDING",
    current: true,
    reviews: [],
    duplicateReceiptDigests: [],
  };
}
