import type { LeaveEvidenceOptionsDto } from "../models/leave-evidence-option-dto";
import type { LeavePreparationDirectoryDto } from "../models/leave-preparation-employee-dto";
import type { LeaveRequestPreviewDto } from "../models/leave-request-preview-dto";
import type { LeaveRequestSelectionDto } from "../models/leave-request-submission-dto";
import type { LeaveTypePageDto } from "../models/leave-type-dto";

export interface LeavePreparationDataSource {
  types(
    company: string,
    asOf: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveTypePageDto>;
  employees(
    company: string,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeavePreparationDirectoryDto>;
  evidence(
    company: string,
    employee: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LeaveEvidenceOptionsDto>;
  preview(
    company: string,
    selection: LeaveRequestSelectionDto,
    signal: AbortSignal,
  ): Promise<LeaveRequestPreviewDto>;
}
