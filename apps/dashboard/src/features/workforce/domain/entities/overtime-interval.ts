export type OvertimeInterval = Readonly<{
  startsAt: string;
  endsAt: string;
  breakMinutes: number;
  workedMinutes: number;
}>;

export type OvertimeIntervalInput = Readonly<Omit<OvertimeInterval, "workedMinutes">>;
