import { expect, it } from "vitest";
import type { AccountId, CompanyId } from "../../core/domain/identifiers";
import type {
  ApprovalId,
  ApprovalRequest,
} from "../../features/approvals/domain/entities/approval-request";
import { approvalResourceRoute } from "./approval-resource-route";

it("opens the company-scoped amendment review referenced by an approval request", () => {
  const request: ApprovalRequest = {
    id: "approval" as ApprovalId,
    companyId: "company" as CompanyId,
    kind: "PAYROLL_AMENDMENT",
    resourceId: "review/id",
    authorId: "author" as AccountId,
    requesterId: null,
    templateId: "template",
    templateRevision: 0,
    stages: [{ assignees: ["reviewer" as AccountId] }],
    currentStep: 0,
    status: "PENDING",
    version: 0,
    submittedAt: "2026-10-01T01:00:00Z",
    excludedAccountIds: [],
  };
  expect(approvalResourceRoute(request)).toBe(
    "/payroll/amendment-reviews/review%2Fid?company=company",
  );
  expect(approvalResourceRoute({ ...request, kind: "PAYROLL" })).toBe(
    "/payroll/reviews/review%2Fid?company=company",
  );
});
