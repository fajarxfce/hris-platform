import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LeaveAccrualScheduleId } from "../../domain/entities/leave-accrual-schedule";
import type { LeaveAccrualScheduleCreation } from "../../domain/entities/leave-accrual-schedule-command";
import type { LeaveAccrualScheduleSelection } from "../../domain/entities/leave-accrual-schedule-review";
import type { LeaveAccrualScheduleRepository } from "../../domain/repositories/leave-accrual-schedule-repository";
import type { LeaveAccrualScheduleDataSource } from "../datasources/leave-accrual-schedule-data-source";
import {
  toLeaveAccrualScheduleDetails,
  toLeaveAccrualScheduleReceipt,
  toLeaveAccrualScheduleReview,
  toLeaveAccrualSchedules,
} from "../mappers/leave-accrual-schedule-mapper";
export class RemoteLeaveAccrualScheduleRepository implements LeaveAccrualScheduleRepository {
  constructor(private readonly source: LeaveAccrualScheduleDataSource) {}
  review(company: CompanyId, input: LeaveAccrualScheduleSelection, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveAccrualScheduleReview(
        await this.source.review(company, input, signal),
        company,
        input,
      ),
    );
  }
  list(company: CompanyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveAccrualSchedules(await this.source.list(company, after, signal), company, after),
    );
  }
  get(company: CompanyId, id: LeaveAccrualScheduleId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveAccrualScheduleDetails(await this.source.get(company, id, signal), company, id),
    );
  }
  create(operation: OperationId, input: LeaveAccrualScheduleCreation, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveAccrualScheduleReceipt(
        await this.source.create(
          input.companyId,
          operation,
          {
            id: input.id,
            typeId: input.typeId,
            firstPeriod: input.firstPeriod,
            periods: input.periods,
            expectedPolicyVersion: input.expectedPolicyVersion,
            employeeIds: input.employeeIds,
            reason: input.reason,
          },
          signal,
        ),
        input.id,
      ),
    );
  }
}
