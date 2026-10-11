export type AttendanceReviewChange = Readonly<{
  entryId: string;
  version: number;
  decision: "ACCEPT" | "REJECT";
  reason: string;
}>;
