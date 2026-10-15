import type { PayrollAmendmentCommand } from "./payroll-amendment-command";

export type PayrollAmendmentPublicationStart = PayrollAmendmentCommand &
  Readonly<{
    publicationId: string;
    reviewId: string;
    expectedReviewVersion: number;
    expectedApprovalVersion: number;
    calculationId: string;
  }>;
export type PayrollAmendmentPublicationRecovery = PayrollAmendmentCommand &
  Readonly<{
    expectedJobVersion: number;
  }>;
