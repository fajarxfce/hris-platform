import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LeaveBalanceAdjustment } from "../../domain/entities/leave-balance-adjustment";
import type { LeaveBalanceRepository } from "../../domain/repositories/leave-balance-repository";
import type { LeaveBalanceDataSource } from "../datasources/leave-balance-data-source";
import { toEmployeeLeaveBalances } from "../mappers/leave-balance-mapper";
import { toLeaveLedger } from "../mappers/leave-ledger-mapper";

export class RemoteLeaveBalanceRepository implements LeaveBalanceRepository {
  constructor(private readonly source: LeaveBalanceDataSource) {}
  adjust(
    company: CompanyId,
    operation: OperationId,
    change: LeaveBalanceAdjustment,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () => {
      const receipt = await this.source.adjust(
        company,
        operation,
        change.employeeId,
        change.typeId,
        change.year,
        { days: change.days, reason: change.reason, expectedVersion: change.expectedVersion },
        signal,
      );
      // The server assigns an immutable movement ID, not the balance account's next version.
      if (receipt.version !== 0) throw new InvalidHttpResponseError();
      return Object.freeze({ id: receipt.id.toLowerCase(), version: 0 });
    });
  }
  list(
    company: CompanyId,
    employee: string,
    year: number,
    after: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toEmployeeLeaveBalances(
        await this.source.list(company, employee, year, after, signal),
        company,
        employee,
        year,
        after,
      ),
    );
  }
  ledger(
    company: CompanyId,
    employee: string,
    type: string,
    year: number,
    after: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLeaveLedger(
        await this.source.ledger(company, employee, type, year, after, signal),
        company,
        employee,
        type,
        year,
        after,
      ),
    );
  }
}
