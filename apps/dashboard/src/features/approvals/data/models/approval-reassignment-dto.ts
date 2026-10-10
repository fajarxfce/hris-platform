export type ApprovalReassignmentDto = Readonly<{
  version: number;
  assignees: readonly string[];
  reason: string;
}>;
