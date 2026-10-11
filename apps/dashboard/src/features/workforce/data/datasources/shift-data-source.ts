import type {
  ShiftChangeDto,
  ShiftDto,
  ShiftPageDto,
  ShiftReceiptDto,
  ShiftSearchDto,
} from "../models/shift-dto";

export interface ShiftDataSource {
  list(company: string, search: ShiftSearchDto, signal: AbortSignal): Promise<ShiftPageDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<ShiftDto>;
  save(
    company: string,
    id: string,
    operation: string,
    body: ShiftChangeDto,
    signal: AbortSignal,
  ): Promise<ShiftReceiptDto>;
}
