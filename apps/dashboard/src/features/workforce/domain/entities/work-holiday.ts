import type { CompanyId } from "../../../../core/domain/identifiers";

export type HolidayId = string & { readonly holidayId: unique symbol };
export type WorkHoliday = Readonly<{
  id: HolidayId;
  companyId: CompanyId;
  workDate: string;
  name: string;
  active: boolean;
  version: number;
}>;
