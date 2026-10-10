import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../domain/entities/employee";
import type { PersonProfileChange } from "../../domain/entities/person-profile";
import type { PersonProfileRepository } from "../../domain/repositories/person-profile-repository";
import type { PersonProfileDataSource } from "../datasources/person-profile-data-source";
import {
  toPersonProfile,
  toPersonProfileChangeDto,
  toPersonProfileHistory,
  toPersonProfileReceipt,
} from "../mappers/person-profile-mapper";

export class RemotePersonProfileRepository implements PersonProfileRepository {
  constructor(private readonly source: PersonProfileDataSource) {}
  get(companyId: CompanyId, employeeId: EmployeeId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPersonProfile(await this.source.get(companyId, employeeId, signal)),
    );
  }
  history(companyId: CompanyId, employeeId: EmployeeId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPersonProfileHistory(
        await this.source.history(companyId, employeeId, after, signal),
        after,
      ),
    );
  }
  save(
    companyId: CompanyId,
    employeeId: EmployeeId,
    operation: OperationId,
    change: PersonProfileChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPersonProfileReceipt(
        await this.source.save(
          companyId,
          employeeId,
          operation,
          toPersonProfileChangeDto(change),
          signal,
        ),
        change,
      ),
    );
  }
}
