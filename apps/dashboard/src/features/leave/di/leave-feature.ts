import type { HttpClient } from "../../../core/data/http/http-client";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpLeaveAccrualScheduleDataSource } from "../data/datasources/http-leave-accrual-schedule-data-source";
import { HttpLeaveBalanceDataSource } from "../data/datasources/http-leave-balance-data-source";
import { HttpLeaveBatchDataSource } from "../data/datasources/http-leave-batch-data-source";
import { HttpLeaveCalendarDataSource } from "../data/datasources/http-leave-calendar-data-source";
import { HttpLeavePolicyDataSource } from "../data/datasources/http-leave-policy-data-source";
import { HttpLeavePreparationDataSource } from "../data/datasources/http-leave-preparation-data-source";
import { HttpLeaveRequestDataSource } from "../data/datasources/http-leave-request-data-source";
import { RemoteLeaveAccrualScheduleRepository } from "../data/repositories/remote-leave-accrual-schedule-repository";
import { RemoteLeaveBalanceRepository } from "../data/repositories/remote-leave-balance-repository";
import { RemoteLeaveBatchRepository } from "../data/repositories/remote-leave-batch-repository";
import { RemoteLeaveCalendarRepository } from "../data/repositories/remote-leave-calendar-repository";
import { RemoteLeavePolicyRepository } from "../data/repositories/remote-leave-policy-repository";
import { RemoteLeavePreparationRepository } from "../data/repositories/remote-leave-preparation-repository";
import { RemoteLeaveRequestRepository } from "../data/repositories/remote-leave-request-repository";
import { AdjustLeaveBalance } from "../domain/usecases/adjust-leave-balance";
import { DecideLeaveRequest } from "../domain/usecases/decide-leave-request";
import { DownloadLeaveAttachment } from "../domain/usecases/download-leave-attachment";
import { LoadEmployeeLeaveBalances } from "../domain/usecases/load-employee-leave-balances";
import { LoadLeaveAccrualSchedule } from "../domain/usecases/load-leave-accrual-schedule";
import { LoadLeaveAccrualSchedules } from "../domain/usecases/load-leave-accrual-schedules";
import { LoadLeaveAdjustmentCatalog } from "../domain/usecases/load-leave-adjustment-catalog";
import { LoadLeaveBatch } from "../domain/usecases/load-leave-batch";
import { LoadLeaveBatchTypes } from "../domain/usecases/load-leave-batch-types";
import { LoadLeaveBatches } from "../domain/usecases/load-leave-batches";
import { LoadLeaveCalendar } from "../domain/usecases/load-leave-calendar";
import { LoadLeaveCalendarEntries } from "../domain/usecases/load-leave-calendar-entries";
import { LoadLeaveLedger } from "../domain/usecases/load-leave-ledger";
import { LoadLeavePolicies } from "../domain/usecases/load-leave-policies";
import { LoadLeavePolicy } from "../domain/usecases/load-leave-policy";
import { LoadLeavePreparationEmployees } from "../domain/usecases/load-leave-preparation-employees";
import { LoadLeavePreparationEvidence } from "../domain/usecases/load-leave-preparation-evidence";
import { LoadLeaveRequest } from "../domain/usecases/load-leave-request";
import { LoadLeaveRequestTypes } from "../domain/usecases/load-leave-request-types";
import { LoadLeaveRequests } from "../domain/usecases/load-leave-requests";
import { PreviewLeaveRequest } from "../domain/usecases/preview-leave-request";
import { RequestLeaveCancellation } from "../domain/usecases/request-leave-cancellation";
import { ResumeLeaveBatch } from "../domain/usecases/resume-leave-batch";
import { ReviewLeaveAccrualSchedule } from "../domain/usecases/review-leave-accrual-schedule";
import { ReviewLeaveAction } from "../domain/usecases/review-leave-action";
import { ReviewLeaveBalanceAdjustment } from "../domain/usecases/review-leave-balance-adjustment";
import { ReviewLeaveBatch } from "../domain/usecases/review-leave-batch";
import { SaveLeavePolicy } from "../domain/usecases/save-leave-policy";
import { ScheduleLeaveAccrual } from "../domain/usecases/schedule-leave-accrual";
import { StartLeaveBatch } from "../domain/usecases/start-leave-batch";
import { SubmitLeaveRequest } from "../domain/usecases/submit-leave-request";
import { WithdrawLeaveRequest } from "../domain/usecases/withdraw-leave-request";

export function createLeaveFeature(
  http: HttpClient,
  files: Pick<FileRepository, "downloadBinary">,
) {
  const schedules = new RemoteLeaveAccrualScheduleRepository(
    new HttpLeaveAccrualScheduleDataSource(http),
  );
  const calendar = new RemoteLeaveCalendarRepository(new HttpLeaveCalendarDataSource(http));
  const batches = new RemoteLeaveBatchRepository(new HttpLeaveBatchDataSource(http));
  const requests = new RemoteLeaveRequestRepository(new HttpLeaveRequestDataSource(http));
  const policies = new RemoteLeavePolicyRepository(new HttpLeavePolicyDataSource(http));
  const balances = new RemoteLeaveBalanceRepository(new HttpLeaveBalanceDataSource(http));
  const preparation = new RemoteLeavePreparationRepository(
    new HttpLeavePreparationDataSource(http),
  );
  return {
    loadAccrualSchedule: new LoadLeaveAccrualSchedule(schedules),
    loadAccrualSchedules: new LoadLeaveAccrualSchedules(schedules),
    reviewAccrualSchedule: new ReviewLeaveAccrualSchedule(schedules),
    scheduleAccrual: new ScheduleLeaveAccrual(schedules),
    loadCalendar: new LoadLeaveCalendar(calendar),
    loadCalendarEntries: new LoadLeaveCalendarEntries(calendar),
    loadBatchTypes: new LoadLeaveBatchTypes(batches),
    reviewBatch: new ReviewLeaveBatch(batches),
    loadBatches: new LoadLeaveBatches(batches),
    loadBatch: new LoadLeaveBatch(batches),
    startBatch: new StartLeaveBatch(batches),
    resumeBatch: new ResumeLeaveBatch(batches),

    loadPreparationEmployees: new LoadLeavePreparationEmployees(preparation),
    loadPreparationEvidence: new LoadLeavePreparationEvidence(preparation),
    loadRequestTypes: new LoadLeaveRequestTypes(preparation),
    previewRequest: new PreviewLeaveRequest(preparation),
    submitRequest: new SubmitLeaveRequest(requests),
    adjustBalance: new AdjustLeaveBalance(balances),
    reviewBalanceAdjustment: new ReviewLeaveBalanceAdjustment(balances),
    loadAdjustmentCatalog: new LoadLeaveAdjustmentCatalog(balances, policies),
    loadBalances: new LoadEmployeeLeaveBalances(balances),
    loadLedger: new LoadLeaveLedger(balances),
    savePolicy: new SaveLeavePolicy(policies),
    loadPolicies: new LoadLeavePolicies(policies),
    loadPolicy: new LoadLeavePolicy(policies),
    downloadAttachment: new DownloadLeaveAttachment(requests, files),
    loadRequests: new LoadLeaveRequests(requests),
    loadRequest: new LoadLeaveRequest(requests),
    reviewAction: new ReviewLeaveAction(requests),
    decide: new DecideLeaveRequest(requests),
    withdraw: new WithdrawLeaveRequest(requests),
    requestCancellation: new RequestLeaveCancellation(requests),
  };
}
