import { describe, expect, it, vi } from "vitest";
import {
  amendmentPublicationDetails,
  amendmentPublicationId,
} from "../../../../../tests/fixtures/payroll-amendment-publications";
import { amendmentReviewId } from "../../../../../tests/fixtures/payroll-amendment-reviews";
import {
  amendmentCalculationId,
  amendmentId,
} from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentPublicationDetails } from "../../data/mappers/payroll-amendment-publication-mapper";
import { amendmentPublicationStartFailure } from "../policies/payroll-amendment-publication-policy";
import type { PayrollAmendmentPublicationRepository } from "../repositories/payroll-amendment-publication-repository";
import { RecoverPayrollAmendmentPublication } from "./recover-payroll-amendment-publication";
import { StartPayrollAmendmentPublication } from "./start-payroll-amendment-publication";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.amend.publish"],
  },
  operation = amendmentId(50) as OperationId,
  signal = new AbortController().signal,
  start = {
    publicationId: amendmentPublicationId(),
    expectedVersion: 3,
    reviewId: amendmentReviewId(),
    expectedReviewVersion: 1,
    expectedApprovalVersion: 1,
    calculationId: amendmentCalculationId,
    reason: "Publish reviewed correction",
  },
  recovery = { expectedVersion: 4, expectedJobVersion: 2, reason: "Close exhausted attempt" };
function repository() {
  return {
    list: vi.fn<PayrollAmendmentPublicationRepository["list"]>(),
    start: vi
      .fn<PayrollAmendmentPublicationRepository["start"]>()
      .mockResolvedValue(success({ id: amendmentId(), version: 4 })),
    recover: vi
      .fn<PayrollAmendmentPublicationRepository["recover"]>()
      .mockResolvedValue(success({ id: amendmentId(), version: 5 })),
  };
}
describe("publication original command policy", () => {
  it("replays the original approved basis without a current-state read, even after worker completion", async () => {
    const repo = repository(),
      command = new StartPayrollAmendmentPublication(repo),
      input = {
        ...start,
        publicationId: start.publicationId.toUpperCase(),
        reviewId: start.reviewId.toUpperCase(),
        calculationId: start.calculationId.toUpperCase(),
        reason: `  ${start.reason}  `,
      };
    expect(
      await command.execute(access, amendmentId().toUpperCase(), operation, input, signal),
    ).toEqual(success({ id: amendmentId(), version: 4 }));
    await command.execute(access, amendmentId(), operation, input, signal);
    expect(repo.start).toHaveBeenCalledTimes(2);
    expect(repo.start.mock.calls[0]).toEqual([
      access.companyId,
      amendmentId(),
      operation,
      start,
      signal,
    ]);
    expect(repo.start.mock.calls[1]).toEqual(repo.start.mock.calls[0]);
    expect(Object.isFrozen(repo.start.mock.calls[0]?.[3])).toBe(true);
    expect(repo.list).not.toHaveBeenCalled();
    expect(repo.recover).not.toHaveBeenCalled();
  });
  it("recovers the observed job once per explicit request and never queues another publication", async () => {
    const repo = repository(),
      command = new RecoverPayrollAmendmentPublication(repo);
    await command.execute(
      access,
      amendmentId().toUpperCase(),
      amendmentPublicationId().toUpperCase(),
      operation,
      { ...recovery, reason: ` ${recovery.reason} ` },
      signal,
    );
    await command.execute(
      access,
      amendmentId(),
      amendmentPublicationId(),
      operation,
      recovery,
      signal,
    );
    expect(repo.recover.mock.calls[0]).toEqual([
      access.companyId,
      amendmentId(),
      amendmentPublicationId(),
      operation,
      recovery,
      signal,
    ]);
    expect(repo.recover.mock.calls[1]).toEqual(repo.recover.mock.calls[0]);
    expect(Object.isFrozen(repo.recover.mock.calls[0]?.[4])).toBe(true);
    expect(repo.list).not.toHaveBeenCalled();
    expect(repo.start).not.toHaveBeenCalled();
  });
  it.each(["payroll.amend", "payroll.amend.review", "payroll.finalize", "jobs.manage"])(
    "does not grant publication through %s",
    async (permission) => {
      const repo = repository(),
        actor = { ...access, permissions: [permission] };
      expect(
        await new StartPayrollAmendmentPublication(repo).execute(
          actor,
          amendmentId(),
          operation,
          start,
          signal,
        ),
      ).toEqual(failed("access_denied"));
      expect(
        await new RecoverPayrollAmendmentPublication(repo).execute(
          actor,
          amendmentId(),
          amendmentPublicationId(),
          operation,
          recovery,
          signal,
        ),
      ).toEqual(failed("access_denied"));
      expect(repo.start).not.toHaveBeenCalled();
      expect(repo.recover).not.toHaveBeenCalled();
    },
  );
  it.each([
    { expectedVersion: -1 },
    { expectedVersion: 997 },
    { expectedVersion: 999 },
    { expectedVersion: 3.5 },
    { expectedReviewVersion: 0 },
    { expectedReviewVersion: 9 },
    { expectedReviewVersion: 1.5 },
    { expectedApprovalVersion: 0 },
    { expectedApprovalVersion: Number.MAX_SAFE_INTEGER + 1 },
    { publicationId: "invalid" },
    { reviewId: "invalid" },
    { calculationId: "invalid" },
    { reason: " " },
    { reason: "x".repeat(1001) },
    { reason: "line\nline" },
  ])("rejects inadmissible publication %j before I/O", async (change) => {
    const repo = repository();
    expect(
      (
        await new StartPayrollAmendmentPublication(repo).execute(
          access,
          amendmentId(),
          operation,
          { ...start, ...change },
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(repo.start).not.toHaveBeenCalled();
  });
  it.each([
    { expectedVersion: -1 },
    { expectedVersion: 999 },
    { expectedJobVersion: -1 },
    { expectedJobVersion: 0.5 },
    { expectedJobVersion: Number.MAX_SAFE_INTEGER + 1 },
    { reason: " " },
  ])("rejects invalid recovery %j before I/O", async (change) => {
    const repo = repository();
    expect(
      (
        await new RecoverPayrollAmendmentPublication(repo).execute(
          access,
          amendmentId(),
          amendmentPublicationId(),
          operation,
          { ...recovery, ...change },
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(repo.recover).not.toHaveBeenCalled();
  });
  it("preserves server failure classifications and cancellation", async () => {
    const repo = repository(),
      owner = new AbortController();
    repo.start.mockResolvedValueOnce(failed("payroll_amendment_payment_pending"));
    repo.recover.mockResolvedValueOnce(failed("stale_version"));
    expect(
      await new StartPayrollAmendmentPublication(repo).execute(
        access,
        amendmentId(),
        operation,
        start,
        signal,
      ),
    ).toEqual(failed("payroll_amendment_payment_pending"));
    expect(
      await new RecoverPayrollAmendmentPublication(repo).execute(
        access,
        amendmentId(),
        amendmentPublicationId(),
        operation,
        recovery,
        signal,
      ),
    ).toEqual(failed("stale_version"));
    owner.abort();
    expect(() =>
      new StartPayrollAmendmentPublication(repo).execute(
        access,
        amendmentId(),
        operation,
        start,
        owner.signal,
      ),
    ).toThrow();
    expect(() =>
      new RecoverPayrollAmendmentPublication(repo).execute(
        access,
        amendmentId(),
        amendmentPublicationId(),
        operation,
        recovery,
        owner.signal,
      ),
    ).toThrow();
    expect(repo.start).toHaveBeenCalledTimes(1);
    expect(repo.recover).toHaveBeenCalledTimes(1);
  });
  it("reserves completion revisions and caps attempts without disabling stopped-attempt review", () => {
    const stopped = toPayrollAmendmentPublicationDetails(
      amendmentPublicationDetails("STOPPED"),
      access.companyId,
      amendmentId(),
    );
    expect(
      amendmentPublicationStartFailure({ ...stopped.amendment, version: 996 }, [stopped]),
    ).toBeNull();
    expect(
      amendmentPublicationStartFailure({ ...stopped.amendment, version: 997 }, [stopped])?.code,
    ).toBe("payroll_amendment_revision_limit");
    expect(
      amendmentPublicationStartFailure(
        stopped.amendment,
        Array.from({ length: 8 }, () => stopped),
      )?.code,
    ).toBe("payroll_amendment_publication_limit");
    expect(
      amendmentPublicationStartFailure(stopped.amendment, [
        { ...stopped, publication: { ...stopped.publication, status: "PENDING" } },
      ])?.code,
    ).toBe("payroll_amendment_publication_active");
  });
});
