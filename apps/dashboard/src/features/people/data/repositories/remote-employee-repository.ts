import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmployeeSearch } from "../../domain/entities/employee-search";
import type { EmployeeRepository } from "../../domain/repositories/employee-repository";
import type { EmployeeDataSource } from "../datasources/employee-data-source";
import { toEmployee, toEmployeePage } from "../mappers/employee-mapper";
import { toEmploymentHistoryPage } from "../mappers/employment-history-mapper";

export class RemoteEmployeeRepository implements EmployeeRepository {
  constructor(private readonly source: EmployeeDataSource) {}
  list(companyId: CompanyId, search: EmployeeSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployeePage(await this.source.list(companyId, search, signal), companyId, search),
    );
  }
  get(companyId: CompanyId, id: EmployeeId, asOf: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmployee(await this.source.get(companyId, id, asOf, signal), companyId, asOf, id),
    );
  }
  history(companyId: CompanyId, id: EmployeeId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toEmploymentHistoryPage(await this.source.history(companyId, id, after, signal), after),
    );
  }
}
