import type {
  AssignedLifecycleTaskPageDto,
  LifecycleCaseDto,
  LifecycleCasePageDto,
  LifecycleCaseQuery,
} from "../models/lifecycle-case-dto";
import type { LifecycleHistoryPageDto } from "../models/lifecycle-event-dto";
import type {
  LifecycleTaskChangeDto,
  LifecycleTaskReceiptDto,
} from "../models/lifecycle-task-change-dto";

export interface LifecycleCaseDataSource {
  changeTask(
    company: string,
    id: string,
    key: string,
    operation: string,
    change: LifecycleTaskChangeDto,
    signal: AbortSignal,
  ): Promise<LifecycleTaskReceiptDto>;
  list(
    company: string,
    query: LifecycleCaseQuery,
    signal: AbortSignal,
  ): Promise<LifecycleCasePageDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<LifecycleCaseDto>;
  history(
    company: string,
    id: string,
    after: number | null,
    signal: AbortSignal,
  ): Promise<LifecycleHistoryPageDto>;
  assigned(
    company: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<AssignedLifecycleTaskPageDto>;
}
