import type { LifecycleAssigneePageDto } from "../models/lifecycle-assignee-dto";
import type {
  AssignedLifecycleTaskPageDto,
  LifecycleCaseDto,
  LifecycleCasePageDto,
  LifecycleCaseQuery,
} from "../models/lifecycle-case-dto";
import type {
  LifecycleCaseStartDto,
  LifecycleCaseStartReceiptDto,
} from "../models/lifecycle-case-start-dto";
import type { LifecycleHistoryPageDto } from "../models/lifecycle-event-dto";
import type { LifecycleTaskAssignmentDto } from "../models/lifecycle-task-assignment-dto";
import type {
  LifecycleTaskChangeDto,
  LifecycleTaskReceiptDto,
} from "../models/lifecycle-task-change-dto";

export interface LifecycleCaseDataSource {
  start(
    company: string,
    operation: string,
    command: LifecycleCaseStartDto,
    signal: AbortSignal,
  ): Promise<LifecycleCaseStartReceiptDto>;
  assignees(
    company: string,
    query: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<LifecycleAssigneePageDto>;
  assignTask(
    company: string,
    id: string,
    key: string,
    operation: string,
    change: LifecycleTaskAssignmentDto,
    signal: AbortSignal,
  ): Promise<LifecycleTaskReceiptDto>;
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
