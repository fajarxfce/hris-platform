import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { PersonAccountBinding } from "../domain/entities/person-account-binding";
import type { PersonId } from "../domain/entities/person-profile";
import { createPeopleFeature } from "./people-feature";

const author = "20000000-0000-4000-8000-000000000001" as AccountId;
const employee = "40000000-0000-4000-8000-000000000001";
const operation = "60000000-0000-4000-8000-000000000001" as OperationId;
const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["people.account.link", "people.profile.manage", "identity.manage"],
};
const binding: PersonAccountBinding = {
  personId: "50000000-0000-4000-8000-000000000001" as PersonId,
  ownerCompanyId: access.companyId,
  accountId: "20000000-0000-4000-8000-000000000002" as AccountId,
  expectedVersion: 7,
  reason: " Reviewed employee identity ",
};
const signal = () => new AbortController().signal;

describe("employee account binding boundaries", () => {
  it("requires independent administration and the owning company before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const action = createPeopleFeature({ request }).bindPersonAccount;
    for (const removed of access.permissions) {
      expect(
        await action.execute(
          {
            ...access,
            permissions: access.permissions.filter((permission) => permission !== removed),
          },
          author,
          employee,
          operation,
          binding,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "person_account_link_access_required" } });
    }
    expect(
      await action.execute(
        access,
        author,
        employee,
        operation,
        { ...binding, accountId: author },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "independent_account_binding_required" } });
    expect(
      await action.execute(
        access,
        author,
        employee,
        operation,
        { ...binding, ownerCompanyId: "10000000-0000-4000-8000-000000000002" as CompanyId },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "profile_owner_required" } });
    for (const change of [
      { reason: " " },
      { expectedVersion: -1 },
      { expectedVersion: Number.MAX_SAFE_INTEGER },
      { accountId: "../account" as AccountId },
    ]) {
      expect(
        await action.execute(
          access,
          author,
          employee,
          operation,
          { ...binding, ...change },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_account_binding" } });
    }
    expect(request).not.toHaveBeenCalled();
  });
  it("sends only the binding command and validates the person/version acknowledgement", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ id: binding.personId, version: 8 })
      .mockResolvedValueOnce({ id: employee, version: 8 })
      .mockResolvedValueOnce({ id: binding.personId, version: 9 });
    const action = createPeopleFeature({ request }).bindPersonAccount;
    expect(await action.execute(access, author, employee, operation, binding, signal())).toEqual({
      ok: true,
      value: { id: binding.personId, version: 8 },
    });
    expect(request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${access.companyId}/employees/${employee}/account-link`,
      method: "POST",
      operationId: operation,
      body: {
        accountId: binding.accountId,
        expectedVersion: 7,
        reason: "Reviewed employee identity",
      },
    });
    expect(
      await action.execute(access, author, employee, operation, binding, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    expect(
      await action.execute(access, author, employee, operation, binding, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("retains server conflict codes and propagates cancellation without hidden retries", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValueOnce(
      new HttpResponseError(409, {
        code: "person_account_already_bound",
        detail: "Private profile detail",
      }),
    );
    const action = createPeopleFeature({ request }).bindPersonAccount;
    const result = await action.execute(access, author, employee, operation, binding, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "person_account_already_bound" } });
    expect(JSON.stringify(result)).not.toContain("Private");
    const aborted = new AbortController();
    request.mockImplementationOnce(async () => {
      aborted.abort();
      return { id: binding.personId, version: 8 };
    });
    await expect(
      action.execute(access, author, employee, operation, binding, aborted.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(2);
  });
});
