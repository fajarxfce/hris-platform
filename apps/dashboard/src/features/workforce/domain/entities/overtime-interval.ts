export type OvertimeInterval = Readonly<{
  startsAt: string;
  endsAt: string;
  breakMinutes: number;
  workedMinutes: number;
}>;
