import type { AudienceGroupDto } from "../data/models/audience-group-dto";

export const groupId = "61000000-0000-4000-8000-000000000001";
export const employeeId = (index: number) =>
  `71000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export const groupDetail = (version = 0): AudienceGroupDto => ({
  id: groupId,
  version,
  name: "Field team",
  active: true,
  employmentIds: [employeeId(1)],
  recordedAt: "2026-10-11T00:00:00Z",
  recordedBy: "31000000-0000-4000-8000-000000000001",
  reason: "Group review",
});
