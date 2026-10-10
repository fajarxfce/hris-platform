import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeavePolicyId } from "../../domain/entities/leave-policy-definition";
import type { LeavePolicyRepository } from "../../domain/repositories/leave-policy-repository";
import type { LeavePolicyDataSource } from "../datasources/leave-policy-data-source";
import { toLeavePolicyPage, toLeavePolicyReview } from "../mappers/leave-policy-definition-mapper";

export class RemoteLeavePolicyRepository implements LeavePolicyRepository {
  constructor(private readonly source: LeavePolicyDataSource) {}
  list(company: CompanyId, active: boolean | null, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeavePolicyPage(
        await this.source.list(company, active, after, signal),
        company,
        active,
        after,
      ),
    );
  }
  get(company: CompanyId, id: LeavePolicyId, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeavePolicyReview(await this.source.get(company, id, after, signal), company, id, after),
    );
  }
}
