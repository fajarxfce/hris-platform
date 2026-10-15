import { toLeaveCarryExpiryDetails } from "../data/mappers/leave-carry-expiry-mapper";
import type { LeaveCarryExpiryDetailsDto } from "../data/models/leave-carry-expiry-dto";
import type { LeaveCarryExpiryId } from "../domain/entities/leave-carry-expiry";
import {
  batchAccount,
  batchCompany,
  batchEmployee,
  batchId,
  batchJob,
  batchType,
} from "./leave-batch-fixture";
export const carryExpiryId = "b0000000-abcd-4000-8000-000000000001" as LeaveCarryExpiryId;
export const carryDeadline = "2027-03-31T17:00:00Z";
export const carryWire: LeaveCarryExpiryDetailsDto = {
  expiry: {
    id: carryExpiryId,
    sourceKind: "BATCH",
    sourceId: batchId,
    employeeId: null,
    typeId: batchType,
    typeCode: "ANNUAL",
    typeName: "Annual leave",
    year: 2027,
    timezone: "Asia/Jakarta",
    validThrough: "2027-03-31",
    expiresAt: carryDeadline,
    actorId: batchAccount,
    createdAt: "2027-01-05T03:00:00Z",
    reason: "Reviewed year closing",
    totalEmployees: 3,
    jobId: batchJob,
    version: 0,
    status: "FAILED",
    completedEmployees: 2,
    failureCode: "job_attempts_exhausted",
  },
  attempts: [
    {
      jobId: batchJob,
      number: 1,
      baseCompleted: 0,
      startedAt: "2027-01-05T03:00:00Z",
      reason: "Reviewed year closing",
    },
  ],
  job: {
    id: batchJob,
    status: "FAILED",
    completedItems: 2,
    totalItems: 4,
    attempts: 4,
    version: 6,
    failureCode: "job_attempts_exhausted",
    scheduledFor: carryDeadline,
    availableAt: carryDeadline,
    finishedAt: "2027-04-01T00:00:03Z",
  },
  results: {
    items: [
      {
        ordinal: 1,
        employeeId: batchEmployee(1),
        jobId: batchJob,
        status: "EXPIRED",
        expiredDays: "2.5",
        ledgerId: "c0000000-0000-4000-8000-000000000001",
        recordedAt: "2027-04-01T00:00:01Z",
      },
      {
        ordinal: 2,
        employeeId: batchEmployee(2),
        jobId: batchJob,
        status: "UNCHANGED",
        expiredDays: "0",
        ledgerId: null,
        recordedAt: "2027-04-01T00:00:02Z",
      },
    ],
    nextCursor: null,
  },
  employees: [
    { id: batchEmployee(1), name: "Example Employee", number: "EMP-001" },
    { id: batchEmployee(2), name: "Example Manager", number: "EMP-002" },
  ],
  availableActions: ["RESUME"],
};
export function queuedCarryWire(): LeaveCarryExpiryDetailsDto {
  const wire = structuredClone(carryWire);
  wire.expiry.status = "QUEUED";
  wire.expiry.completedEmployees = 0;
  wire.expiry.failureCode = null;
  wire.job = {
    ...wire.job,
    status: "QUEUED",
    completedItems: 0,
    attempts: 0,
    version: 0,
    failureCode: null,
    finishedAt: null,
  };
  wire.results.items = [];
  wire.employees = [];
  wire.availableActions = [];
  return wire;
}
export function resumedCarryWire(): LeaveCarryExpiryDetailsDto {
  const wire = structuredClone(carryWire);
  const jobId = "60000000-0000-4000-8000-000000000002";
  wire.expiry = { ...wire.expiry, jobId, version: 1, status: "QUEUED", failureCode: null };
  wire.attempts.push({
    jobId,
    number: 2,
    baseCompleted: 2,
    startedAt: "2027-04-01T00:01:00Z",
    reason: "Continue remaining work",
  });
  wire.job = {
    ...wire.job,
    id: jobId,
    status: "QUEUED",
    completedItems: 0,
    totalItems: 2,
    attempts: 0,
    version: 0,
    failureCode: null,
    finishedAt: null,
    scheduledFor: "2027-04-01T00:01:00Z",
    availableAt: "2027-04-01T00:01:00Z",
  };
  wire.availableActions = [];
  return wire;
}
export const carryDetails = toLeaveCarryExpiryDetails(carryWire, batchCompany, carryExpiryId, null);
