export type PayrollWorkDay = Readonly<{
  date: string;
  schedule: "WORK" | "OFF" | "UNASSIGNED";
  attendance: "WORKED" | "ABSENCE_RECORDED" | "UNRECORDED" | "OFF" | "UNASSIGNED";
  officialHoliday: boolean;
  overtime: readonly Readonly<{ requestId: string; revision: number; minutes: number }>[];
}>;
