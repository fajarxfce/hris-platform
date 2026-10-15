export type PayrollReviewApproval = Readonly<{
  id: string;
  status: "PENDING" | "BLOCKED" | "APPROVED" | "REJECTED" | "CANCELLED";
  version: number;
  currentStep: number;
  templateId: string;
  templateRevision: number;
  stages: readonly (readonly string[])[];
  authorId: string;
  excludedAccountIds: readonly string[];
}>;
export type PayrollReviewAccount = Readonly<{ id: string; displayName: string }>;
