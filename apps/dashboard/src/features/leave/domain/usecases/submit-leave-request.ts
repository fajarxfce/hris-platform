import type { OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveRequestSubmission } from "../entities/leave-request-submission";
import { canPrepareLeave, validateLeaveSubmission } from "../policies/leave-preparation-policy";
import type { LeaveRequestRepository } from "../repositories/leave-request-repository";

export class SubmitLeaveRequest {
  constructor(private readonly requests: Pick<LeaveRequestRepository, "submit">) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: LeaveRequestSubmission,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canPrepareLeave(access.permissions) || input.companyId !== access.companyId)
      return Promise.resolve(failed("access_denied"));
    const command = validateLeaveSubmission(operation, input);
    return command.ok
      ? this.requests.submit(access.companyId, operation, command.value, signal)
      : Promise.resolve(command);
  }
}
