import type { OvertimeInterval } from "./overtime-interval";

export type OvertimeActualTime = Readonly<Omit<OvertimeInterval, "workedMinutes">>;
