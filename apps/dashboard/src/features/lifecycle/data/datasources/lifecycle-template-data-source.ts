import type {
  LifecycleTemplateChangeDto,
  LifecycleTemplateReceiptDto,
} from "../models/lifecycle-template-change-dto";
import type {
  LifecycleTemplateDto,
  LifecycleTemplatePageDto,
} from "../models/lifecycle-template-dto";

export interface LifecycleTemplateDataSource {
  list(
    company: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LifecycleTemplatePageDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<LifecycleTemplateDto>;
  save(
    company: string,
    id: string,
    operation: string,
    change: LifecycleTemplateChangeDto,
    signal: AbortSignal,
  ): Promise<LifecycleTemplateReceiptDto>;
}
