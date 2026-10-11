export type HolidayChange = Readonly<{
  id: string;
  workDate: string;
  name: string;
  active: boolean;
  expectedVersion: number | null;
  reason: string;
}>;
