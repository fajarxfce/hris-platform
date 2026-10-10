import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { PersonProfileDto, PersonProfileRevisionDto } from "../data/models/person-profile-dto";
import type { PersonId, PersonProfileChange } from "../domain/entities/person-profile";
import { createPeopleFeature } from "./people-feature";

const company = "10000000-0000-4000-8000-000000000001" as CompanyId;
const otherCompany = "10000000-0000-4000-8000-000000000002" as CompanyId;
const employee = "40000000-abcd-4000-8000-000000000001";
const person = "50000000-abcd-4000-8000-000000000001" as PersonId;
const operation = "60000000-0000-4000-8000-000000000001" as OperationId;
const access = {
  companyId: company,
  permissions: ["people.profile.read", "people.profile.manage"],
};
const profile = (): PersonProfileDto => ({
  personId: person,
  ownerCompanyId: company,
  accountId: null,
  legalName: "Private profile",
  birthDate: "1995-06-07",
  nationality: "ID",
  email: "person@internal",
  version: 7,
});
const change = (): PersonProfileChange => ({
  personId: person,
  ownerCompanyId: company,
  legalName: " Updated profile ",
  birthDate: null,
  nationality: " id ",
  email: " PERSON@INTERNAL ",
  expectedVersion: 7,
  reason: " Verified correction ",
});
const revision = (index: number): PersonProfileRevisionDto => ({
  legalName: "Previous name",
  birthDate: "1995-06-07",
  nationality: "ID",
  email: null,
  revision: index,
  actorId: index === 0 ? null : employee,
  accountId: null,
  reason: "Profile revision",
  recordedAt: "2026-01-01T00:00:00.000123Z",
});
const signal = () => new AbortController().signal;

describe("private person profile contracts", () => {
  it("keeps directory, sensitive-read, self, history and management permissions separate before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(profile());
    const feature = createPeopleFeature({ request });
    for (const permission of [
      "people.read",
      "people.team.read",
      "people.manage",
      "people.profile.manage",
    ]) {
      const scope = { ...access, permissions: [permission] };
      expect(await feature.loadPersonProfile.execute(scope, employee, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
      expect(
        await feature.savePersonProfile.execute(scope, employee, operation, change(), signal()),
      ).toMatchObject({ ok: false });
    }
    expect(
      await feature.loadPersonProfileHistory.execute(
        { ...access, permissions: ["people.self.read"] },
        employee,
        null,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(
      await feature.savePersonProfile.execute(
        access,
        employee,
        operation,
        { ...change(), ownerCompanyId: otherCompany },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "profile_owner_required" } });
    expect(request).not.toHaveBeenCalled();
    for (const permission of ["people.profile.read", "people.self.read"])
      expect(
        await feature.loadPersonProfile.execute(
          { ...access, permissions: [permission] },
          employee,
          signal(),
        ),
      ).toMatchObject({ ok: true });
    expect(request).toHaveBeenCalledTimes(2);
  });

  it("validates resource IDs, revisions and fields without issuing an invalid command", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createPeopleFeature({ request });
    expect(await feature.loadPersonProfile.execute(access, "../another", signal())).toMatchObject({
      ok: false,
    });
    for (const after of ["-1", "01", "", "1e2", "9007199254740992"])
      expect(
        await feature.loadPersonProfileHistory.execute(access, employee, after, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    for (const updated of [
      { ...change(), legalName: " " },
      { ...change(), reason: " " },
      { ...change(), reason: "r".repeat(1001) },
      { ...change(), birthDate: "2026-02-30" },
      { ...change(), nationality: "Indonesia" },
      { ...change(), email: "invalid" },
      { ...change(), expectedVersion: -1 },
      { ...change(), expectedVersion: Number.MAX_SAFE_INTEGER },
    ])
      expect(
        await feature.savePersonProfile.execute(access, employee, operation, updated, signal()),
      ).toMatchObject({ ok: false });
    expect(request).not.toHaveBeenCalled();
  });

  it("maps immutable sensitive data only through the separate endpoint", async () => {
    const dto = { ...profile(), personId: person.toUpperCase(), ignored: "DO NOT RETAIN" };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const result = await createPeopleFeature({ request }).loadPersonProfile.execute(
      access,
      employee.toUpperCase(),
      signal(),
    );
    expect(request.mock.calls[0]?.[0].path).toBe(
      `/api/v1/companies/${company}/employees/${employee}/profile`,
    );
    expect(result).toMatchObject({
      ok: true,
      value: { personId: person, birthDate: "1995-06-07", version: 7 },
    });
    if (!result.ok) throw new Error("Profile fixture was rejected");
    expect(Object.isFrozen(result.value)).toBe(true);
    dto.legalName = "Mutated outside repository";
    expect(result.value.legalName).toBe("Private profile");
    expect(JSON.stringify(result)).not.toContain("DO NOT RETAIN");
    for (const broken of [
      { ...profile(), birthDate: "2026-02-30" },
      { ...profile(), legalName: " " },
      { ...profile(), version: Number.MAX_SAFE_INTEGER + 1 },
    ]) {
      request.mockResolvedValueOnce(broken);
      expect(
        await createPeopleFeature({ request }).loadPersonProfile.execute(
          access,
          employee,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("keeps profile versions and person receipts independent from employment identity", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ id: person, version: 8 });
    const feature = createPeopleFeature({ request });
    const result = await feature.savePersonProfile.execute(
      access,
      employee,
      operation,
      change(),
      signal(),
    );
    expect(result).toEqual({ ok: true, value: { id: person, version: 8 } });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/employees/${employee}/profile`,
      method: "PUT",
      operationId: operation,
      body: {
        legalName: "Updated profile",
        birthDate: null,
        nationality: "ID",
        email: "person@internal",
        expectedVersion: 7,
        reason: "Verified correction",
      },
    });
    for (const receipt of [
      { id: employee, version: 8 },
      { id: person, version: 7 },
      { id: person, version: 9 },
    ]) {
      request.mockResolvedValueOnce(receipt);
      expect(
        await feature.savePersonProfile.execute(access, employee, operation, change(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("retains bounded history with nullable initial attribution and exact cursor progress", async () => {
    const items = Array.from({ length: 50 }, (_, index) => revision(index));
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({ items, nextCursor: "49" });
    const feature = createPeopleFeature({ request });
    const result = await feature.loadPersonProfileHistory.execute(access, employee, null, signal());
    expect(result).toMatchObject({ ok: true, value: { nextCursor: "49" } });
    if (!result.ok) throw new Error("History fixture was rejected");
    expect(Object.isFrozen(result.value.items)).toBe(true);
    expect(Object.isFrozen(result.value.items[0])).toBe(true);
    expect(result.value.items[0]?.actorId).toBeNull();
    request.mockResolvedValueOnce({ items: [revision(51)], nextCursor: null });
    expect(
      await feature.loadPersonProfileHistory.execute(access, employee, "49", signal()),
    ).toMatchObject({ ok: true });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${company}/employees/${employee}/profile/history?limit=50&after=49`,
    );
    for (const page of [
      { items: [revision(0), revision(0)], nextCursor: null },
      { items, nextCursor: "048" },
      { items: [revision(0)], nextCursor: "0" },
      { items: [...items, revision(50)], nextCursor: null },
      { items: [{ ...revision(0), recordedAt: "yesterday" }], nextCursor: null },
      { items: [{ ...revision(0), birthDate: "2026-02-30" }], nextCursor: null },
    ]) {
      request.mockResolvedValueOnce(page);
      expect(
        await feature.loadPersonProfileHistory.execute(access, employee, null, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("keeps stable failures and propagates cancellation without a hidden retry", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(409, {
        code: "stale_version",
        fields: {},
        parameters: {},
        detail: "PRIVATE DIAGNOSTIC",
      }),
    );
    const feature = createPeopleFeature({ request });
    expect(
      await feature.savePersonProfile.execute(access, employee, operation, change(), signal()),
    ).toEqual({ ok: false, failure: { code: "stale_version", fields: {}, parameters: {} } });
    expect(request).toHaveBeenCalledTimes(1);
    const cancelled = new AbortController();
    cancelled.abort();
    await expect(
      feature.loadPersonProfile.execute(access, employee, cancelled.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(request).toHaveBeenCalledTimes(1);
  });
});
