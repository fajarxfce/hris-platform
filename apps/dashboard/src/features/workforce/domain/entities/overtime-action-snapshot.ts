import type { OvertimeRequest } from "./overtime-request";
import type { OvertimeAction, OvertimeRequestDetails } from "./overtime-request-details";

export type OvertimeActionSnapshot = Readonly<
  Pick<OvertimeRequest, "id" | "companyId" | "version" | "status" | "requested"> & {
    actions: readonly OvertimeAction[];
  }
>;

/** Only the observed command facts survive an uncertain response; history is not retained here. */
export function overtimeActionSnapshot(details: OvertimeRequestDetails): OvertimeActionSnapshot {
  const { request, actions } = details;
  return Object.freeze({
    id: request.id,
    companyId: request.companyId,
    version: request.version,
    status: request.status,
    requested: Object.freeze({ ...request.requested }),
    actions: Object.freeze([...actions]),
  });
}
