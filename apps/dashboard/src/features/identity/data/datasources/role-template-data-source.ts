import type { RoleTemplateChangeDto } from "../models/role-template-change-dto";
import type {
  RoleTemplatePageDto,
  RoleTemplateReceiptDto,
  RoleTemplateReviewDto,
} from "../models/role-template-dto";
export interface RoleTemplateDataSource {
  permissions(company: string, signal: AbortSignal): Promise<string[]>;
  list(company: string, after: string | null, signal: AbortSignal): Promise<RoleTemplatePageDto>;
  get(
    company: string,
    id: string,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<RoleTemplateReviewDto>;
  save(
    company: string,
    id: string,
    operation: string,
    input: RoleTemplateChangeDto,
    signal: AbortSignal,
  ): Promise<RoleTemplateReceiptDto>;
}
