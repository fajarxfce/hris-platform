export type LeaveRequestSelectionDto = Readonly<{
  employeeId: string;
  typeId: string;
  days: readonly Readonly<{ workDate: string; portion: "FULL" | "FIRST_HALF" | "SECOND_HALF" }>[];
}>;
export type LeaveRequestSubmissionDto = Readonly<
  LeaveRequestSelectionDto & {
    id: string;
    reason: string;
    attachmentRevisionIds: readonly string[];
    previewRevision: string;
  }
>;
