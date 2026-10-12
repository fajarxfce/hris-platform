import type { OvertimeRequestId } from "./overtime-request";

export type OvertimeRequestChange = Readonly<{
  id: OvertimeRequestId;
  version: number;
  reason: string;
}>;
