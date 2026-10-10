import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveBalanceRepository } from "../../domain/repositories/leave-balance-repository";
import type { LeaveBalanceDataSource } from "../datasources/leave-balance-data-source";
import { toEmployeeLeaveBalances } from "../mappers/leave-balance-mapper";
import { toLeaveLedger } from "../mappers/leave-ledger-mapper";

export class RemoteLeaveBalanceRepository implements LeaveBalanceRepository {
  constructor(private readonly source: LeaveBalanceDataSource) {}
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
