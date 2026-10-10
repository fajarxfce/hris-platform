import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type {
  OrganizationUnit,
  OrganizationUnitId,
} from "../../../organization/domain/entities/organization-unit";
import type { OrganizationUnitPage } from "../../../organization/domain/entities/organization-unit-page";
import type { LoadOrganizationUnits } from "../../../organization/domain/usecases/load-organization-units";
import type { Employee, EmployeeId } from "../../domain/entities/employee";
import type { LoadEmployees } from "../../domain/usecases/load-employees";
import type { EmployeeAssignmentKind } from "../models/employee-assignment";
import { useEmployeeAssignmentPicker } from "./use-employee-assignment-picker";

const accountId = "20000000-0000-4000-8000-000000000001" as AccountId;
const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const companyTwo = "10000000-0000-4000-8000-000000000002" as CompanyId;
const branch: OrganizationUnit = {
  id: "30000000-0000-4000-8000-000000000001" as OrganizationUnitId,
  companyId,
  code: "B001",
  name: "Branch one",
  kind: "BRANCH",
  parentId: null,
  timezone: "Asia/Jakarta",
  active: true,
  version: 0,
};
const employee: Employee = {
  id: "40000000-0000-4000-8000-000000000001" as EmployeeId,
  companyId,
  employeeNumber: "EMP001",
  legalName: "Manager one",
  email: null,
  version: 0,
  appliedRevision: 0,
  terms: {
    effectiveFrom: "2026-01-01",
    startDate: "2026-01-01",
    endDate: null,
    status: "ACTIVE",
    contract: "PERMANENT",
  },
};
const clients: QueryClient[] = [];
afterEach(() => {
  cleanup();
  for (const client of clients.splice(0)) client.clear();
});
function fixture(kind: EmployeeAssignmentKind = "BRANCH") {
  const client = new QueryClient();
  clients.push(client);
  const units = {
    execute: vi
      .fn<LoadOrganizationUnits["execute"]>()
      .mockResolvedValue(success({ items: [branch], nextCursor: null })),
  };
  const employees = {
    execute: vi
      .fn<LoadEmployees["execute"]>()
      .mockResolvedValue(success({ items: [employee], nextCursor: null })),
  };
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const rendered = renderHook(
    ({ company, kind, date }: { company: CompanyId; kind: EmployeeAssignmentKind; date: string }) =>
      useEmployeeAssignmentPicker(
        accountId,
        { companyId: company, permissions: ["company.read", "people.read"] },
        kind,
        date,
        units,
        employees,
      ),
    { wrapper, initialProps: { company: companyId, kind, date: "2027-01-01" } },
  );
  return { ...rendered, units, employees, client };
}
describe("bounded assignment query ownership", () => {
  it("reads the selected active type and applies search and pages explicitly", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    expect(f.units.execute.mock.lastCall?.[1]).toEqual({
      query: "",
      after: null,
      kind: "BRANCH",
      active: "true",
    });
    expect(f.employees.execute).not.toHaveBeenCalled();
    await act(async () => {
      await f.result.current.query.onChange({ target: { value: " A&B_% " } });
    });
    expect(f.units.execute).toHaveBeenCalledOnce();
    f.units.execute.mockResolvedValueOnce(
      success({
        items: [branch],
        nextCursor: "BRANCH:B001",
      }),
    );
    await act(async () => {
      await f.result.current.apply();
    });
    await waitFor(() => expect(f.result.current.nextCursor).toBe("BRANCH:B001"));
    expect(f.result.current.options).toHaveLength(1);
    expect(f.units.execute.mock.lastCall?.[1].query).toBe("A&B_%");
    f.units.execute.mockResolvedValueOnce(success({ items: [], nextCursor: null }));
    act(() => f.result.current.next());
    await waitFor(() => expect(f.result.current.loading).toBe(false));
    expect(f.units.execute.mock.lastCall?.[1].after).toBe("BRANCH:B001");
    expect(f.result.current.firstPage).toBe(false);
    await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(1));
  });
  it("cancels a superseded company query, rejects its late result and releases cache on disposal", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    let finish!: (value: Result<OrganizationUnitPage>) => void;
    f.units.execute.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    act(() => f.result.current.refresh());
    await waitFor(() => expect(f.units.execute).toHaveBeenCalledTimes(2));
    const signal = f.units.execute.mock.lastCall?.[2];
    f.units.execute.mockResolvedValueOnce(
      success({
        items: [{ ...branch, companyId: companyTwo, name: "Company two branch" }],
        nextCursor: null,
      }),
    );
    f.rerender({ company: companyTwo, kind: "BRANCH", date: "2027-01-01" });
    expect(signal?.aborted).toBe(true);
    await act(async () => {
      finish(success({ items: [branch], nextCursor: null }));
    });
    await waitFor(() => expect(f.result.current.options[0]?.label).toContain("Company two branch"));
    expect(f.result.current.options[0]?.label).not.toContain("Branch one");
    f.unmount();
    await waitFor(() => expect(f.client.getQueryCache().getAll()).toHaveLength(0));
  });
  it("drops displayed rows after a failed refresh and makes no automatic retry", async () => {
    const f = fixture();
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    f.units.execute.mockResolvedValueOnce(failed("mfa_required"));
    act(() => f.result.current.refresh());
    await waitFor(() => expect(f.result.current.failure?.code).toBe("mfa_required"));
    expect(f.result.current.options).toHaveLength(0);
    expect(f.units.execute).toHaveBeenCalledTimes(2);
    act(() => f.result.current.refresh());
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    expect(f.units.execute).toHaveBeenCalledTimes(3);
  });
  it("uses the requested start date and includes only working managers", async () => {
    const f = fixture("MANAGER");
    await waitFor(() => expect(f.result.current.options).toHaveLength(1));
    f.employees.execute.mockResolvedValueOnce(
      success({
        items: [
          employee,
          { ...employee, terms: { ...employee.terms, status: "SUSPENDED" } },
          { ...employee, terms: { ...employee.terms, status: "ENDED" } },
          { ...employee, terms: { ...employee.terms, startDate: "2028-01-01" } },
          { ...employee, terms: { ...employee.terms, endDate: "2026-12-31" } },
          {
            ...employee,
            id: "40000000-0000-4000-8000-000000000002" as EmployeeId,
            terms: { ...employee.terms, status: "PROBATION", endDate: "2027-06-01" },
          },
        ],
        nextCursor: null,
      }),
    );
    f.rerender({ company: companyId, kind: "MANAGER", date: "2027-06-01" });
    await waitFor(() => expect(f.result.current.options).toHaveLength(2));
    expect(f.employees.execute.mock.lastCall?.[1].asOf).toBe("2027-06-01");
    expect(f.units.execute).not.toHaveBeenCalled();
  });
});
