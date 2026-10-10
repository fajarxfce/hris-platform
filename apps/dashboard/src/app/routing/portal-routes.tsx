import { lazy, Suspense } from "react";
import { Link, Route, Routes } from "react-router-dom";
import type { AccountId } from "../../core/domain/identifiers";
import { AppLoading } from "../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import type { AdministrationUseCases } from "../../features/administration/presentation/contracts/administration-use-cases";
import type {
  CompanyAccess,
  CompanyMembership,
} from "../../features/identity/domain/entities/session";
import type { JobsUseCases } from "../../features/jobs/presentation/contracts/jobs-use-cases";
import type { OrganizationUseCases } from "../../features/organization/presentation/contracts/organization-use-cases";
import { OverviewPage } from "../../features/overview/presentation/pages/overview-page";
import type { PeopleUseCases } from "../../features/people/presentation/contracts/people-use-cases";
import type { ReportingUseCases } from "../../features/reporting/presentation/contracts/reporting-use-cases";

const OrganizationScreen = lazy(() =>
  import("../../features/organization/presentation/bindings/organization-screen").then(
    (module) => ({ default: module.OrganizationScreen }),
  ),
);
const OrganizationUnitScreen = lazy(() =>
  import("../../features/organization/presentation/bindings/organization-unit-screen").then(
    (module) => ({ default: module.OrganizationUnitScreen }),
  ),
);

const JobsScreen = lazy(() =>
  import("../../features/jobs/presentation/bindings/jobs-screen").then((module) => ({
    default: module.JobsScreen,
  })),
);

const EmployeesScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employees-screen").then((module) => ({
    default: module.EmployeesScreen,
  })),
);
const EmployeeDetailsScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employee-details-screen").then((module) => ({
    default: module.EmployeeDetailsScreen,
  })),
);

const HeadcountScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/headcount-screen").then((module) => ({
    default: module.HeadcountScreen,
  })),
);

const AuditScreen = lazy(() =>
  import("../../features/administration/presentation/bindings/audit-screen").then((module) => ({
    default: module.AuditScreen,
  })),
);

const ClientPolicyScreen = lazy(() =>
  import("../../features/administration/presentation/bindings/client-policy-screen").then(
    (module) => ({ default: module.ClientPolicyScreen }),
  ),
);

export function PortalRoutes({
  accountId,
  company,
  companies,
  access,
  reporting,
  administration,
  jobs,
  organization,
  people,
  locale,
}: {
  accountId: AccountId;
  company: CompanyMembership | null;
  companies: readonly CompanyMembership[];
  access: CompanyAccess | null;
  reporting: ReportingUseCases;
  administration: AdministrationUseCases;
  jobs: JobsUseCases;
  organization: OrganizationUseCases;
  people: PeopleUseCases;
  locale: Locale;
}) {
  if (!company || !access) return <OverviewPage company={null} locale={locale} />;
  return (
    <Routes>
      <Route path="/" element={<OverviewPage company={company} locale={locale} />} />
      <Route
        path="/organization/units"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OrganizationScreen
              accountId={accountId}
              access={access}
              organization={organization}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/organization/units/:unitId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OrganizationUnitScreen
              accountId={accountId}
              access={access}
              organization={organization}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/employees"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmployeesScreen
              accountId={accountId}
              access={access}
              people={people}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/employees/:employeeId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmployeeDetailsScreen
              accountId={accountId}
              access={access}
              people={people}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/administration/jobs"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <JobsScreen
              accountId={accountId}
              access={access}
              jobs={jobs}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/headcount"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <HeadcountScreen
              accountId={accountId}
              access={access}
              loadHeadcount={reporting.loadHeadcount}
              companies={companies}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/administration/audit"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <AuditScreen
              accountId={accountId}
              access={access}
              searchAudit={administration.searchAudit}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/settings/client-policy"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ClientPolicyScreen
              accountId={accountId}
              access={access}
              loadClientPolicy={administration.loadClientPolicy}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="*"
        element={
          <>
            <AppPageHeader title={messages(locale).pageNotFound} />
            <Link to="/">{messages(locale).overview}</Link>
          </>
        }
      />
    </Routes>
  );
}
