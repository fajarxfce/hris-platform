export type PayrollFinalizationStart = Readonly<{
  id: string;
  reviewId: string;
  expectedRunVersion: number;
  expectedReviewVersion: number;
  expectedApprovalVersion: number;
  reason: string;
}>;
