import type {
  HolidayChangeDto,
  HolidayDto,
  HolidayRangeDto,
  HolidayReceiptDto,
} from "../models/holiday-dto";

export interface HolidayDataSource {
  list(
    company: string,
    range: HolidayRangeDto,
    signal: AbortSignal,
  ): Promise<readonly HolidayDto[]>;
  get(company: string, id: string, signal: AbortSignal): Promise<HolidayDto>;
  save(
    company: string,
    id: string,
    operation: string,
    body: HolidayChangeDto,
    signal: AbortSignal,
  ): Promise<HolidayReceiptDto>;
}
