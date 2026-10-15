import { expect, it } from "vitest";
import {
  payrollAmendmentPublicationPath,
  payrollAmendmentPublicationRecoveryPath,
  payrollAmendmentPublicationStartPath,
  payrollAmendmentPublicationsPath,
} from "./payroll-amendment-publication-route";

it("retains bounded directory context without carrying another company or historical selection into commands", () => {
  const query = new URLSearchParams(
    "company=other&year=2026&query=EMP&after=cursor&revision=4&tab=proposal&assessment=private&historyAfter=3",
  );
  for (const path of [
    payrollAmendmentPublicationsPath("current", "a/b", query),
    payrollAmendmentPublicationPath("current", "a/b", "p/q", query),
    payrollAmendmentPublicationStartPath("current", "a/b", query),
    payrollAmendmentPublicationRecoveryPath("current", "a/b", "p/q", query),
  ]) {
    const url = new URL(path, "http://fixture");
    expect(url.pathname).toContain("/amendments/a%2Fb/");
    expect([...url.searchParams]).toEqual([
      ["company", "current"],
      ["year", "2026"],
      ["query", "EMP"],
      ["after", "cursor"],
    ]);
  }
  expect(query.get("company")).toBe("other");
  expect(query.get("revision")).toBe("4");
  expect(payrollAmendmentPublicationPath("c", "a", "p/q")).toContain("/publications/p%2Fq?");
  expect(payrollAmendmentPublicationRecoveryPath("c", "a", "p")).toContain(
    "/publications/p/recover?",
  );
});
