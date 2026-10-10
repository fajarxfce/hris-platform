import { lazy, Suspense } from "react";
import { Link, Route, Routes } from "react-router-dom";
import type { AccountId } from "../../core/domain/identifiers";
import { AppLoading } from "../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import type { AdministrationUseCases } from "../../features/administration/presentation/contracts/administration-use-cases";
import type { ApprovalsUseCases } from "../../features/approvals/presentation/contracts/approvals-use-cases";
import type {
  CompanyAccess,
  CompanyMembership,
} from "../../features/identity/domain/entities/session";
import type { JobsUseCases } from "../../features/jobs/presentation/contracts/jobs-use-cases";
import type { LifecycleUseCases } from "../../features/lifecycle/presentation/contracts/lifecycle-use-cases";
import type { OrganizationUseCases } from "../../features/organization/presentation/contracts/organization-use-cases";
import { OverviewPage } from "../../features/overview/presentation/pages/overview-page";
import type { PeopleUseCases } from "../../features/people/presentation/contracts/people-use-cases";
import type { ReportingUseCases } from "../../features/reporting/presentation/contracts/reporting-use-cases";

const ApprovalTemplatesScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-templates-screen").then(
    (module) => ({ default: module.ApprovalTemplatesScreen }),
  ),
);
const ApprovalTemplateScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-template-screen").then(
    (module) => ({ default: module.ApprovalTemplateScreen }),
  ),
);
const ApprovalTemplateEditorScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-template-editor-screen").then(
    (module) => ({ default: module.ApprovalTemplateEditorScreen }),
  ),
);

const ApprovalInboxScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-inbox-screen").then((module) => ({
    default: module.ApprovalInboxScreen,
  })),
);
const ApprovalRequestScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-request-screen").then(
    (module) => ({ default: module.ApprovalRequestScreen }),
  ),
);

const LifecycleTemplatesScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/lifecycle-templates-screen").then(
    (module) => ({ default: module.LifecycleTemplatesScreen }),
  ),
);
const LifecycleCaseCreationScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/lifecycle-case-creation-screen").then(
    (module) => ({ default: module.LifecycleCaseCreationScreen }),
  ),
);
const LifecycleCasesScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/lifecycle-cases-screen").then(
    (module) => ({ default: module.LifecycleCasesScreen }),
  ),
);
const LifecycleCaseScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/lifecycle-case-screen").then((module) => ({
    default: module.LifecycleCaseScreen,
  })),
);
const OffboardingScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/offboarding-screen").then((module) => ({
    default: module.OffboardingScreen,
  })),
);
const AssignedLifecycleTasksScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/assigned-lifecycle-tasks-screen").then(
    (module) => ({ default: module.AssignedLifecycleTasksScreen }),
  ),
);
const LifecycleTemplateScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/lifecycle-template-screen").then(
    (module) => ({ default: module.LifecycleTemplateScreen }),
  ),
);
const LifecycleTemplateEditorScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/lifecycle-template-editor-screen").then(
    (module) => ({ default: module.LifecycleTemplateEditorScreen }),
  ),
);

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
const OrganizationEditorScreen = lazy(() =>
  import("../../features/organization/presentation/bindings/organization-editor-screen").then(
    (module) => ({ default: module.OrganizationEditorScreen }),
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
const EmployeeImportsScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employee-imports-screen").then((module) => ({
    default: module.EmployeeImportsScreen,
  })),
);
const EmployeeImportCreationScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employee-import-creation-screen").then(
    (module) => ({ default: module.EmployeeImportCreationScreen }),
  ),
);
const EmployeeImportScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employee-import-screen").then((module) => ({
    default: module.EmployeeImportScreen,
  })),
);
const EmployeeImportTransitionScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employee-import-transition-screen").then(
    (module) => ({ default: module.EmployeeImportTransitionScreen }),
  ),
);
const EmployeeDetailsScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employee-details-screen").then((module) => ({
    default: module.EmployeeDetailsScreen,
  })),
);
const EmployeeCreationScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employee-creation-screen").then((module) => ({
    default: module.EmployeeCreationScreen,
  })),
);
const EmploymentEditorScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employment-editor-screen").then((module) => ({
    default: module.EmploymentEditorScreen,
  })),
);
const PersonProfileScreen = lazy(() =>
  import("../../features/people/presentation/bindings/person-profile-screen").then((module) => ({
    default: module.PersonProfileScreen,
  })),
);
const EmploymentCancellationScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employment-cancellation-screen").then(
    (module) => ({ default: module.EmploymentCancellationScreen }),
  ),
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

const ClientPolicyEditorScreen = lazy(() =>
  import("../../features/administration/presentation/bindings/client-policy-editor-screen").then(
    (module) => ({ default: module.ClientPolicyEditorScreen }),
  ),
);

export function PortalRoutes({
  clientBuild,
  accountId,
  company,
  companies,
  access,
  reporting,
  administration,
  approvals,
  jobs,
  organization,
  people,
  lifecycle,
  locale,
  nextIdentifier,
}: {
  clientBuild: number;
  accountId: AccountId;
  company: CompanyMembership | null;
  companies: readonly CompanyMembership[];
  access: CompanyAccess | null;
  reporting: ReportingUseCases;
  administration: AdministrationUseCases;
  approvals: ApprovalsUseCases;
  jobs: JobsUseCases;
  organization: OrganizationUseCases;
  people: PeopleUseCases;
  lifecycle: LifecycleUseCases;
  locale: Locale;
  nextIdentifier: () => string;
}) {
  if (!company || !access) return <OverviewPage company={null} locale={locale} />;
  return (
    <Routes>
      <Route
        path="/approvals/templates"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalTemplatesScreen
              accountId={accountId}
              access={access}
              approvals={approvals}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/approvals/templates/:templateId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalTemplateScreen
              accountId={accountId}
              access={access}
              approvals={approvals}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/approvals/templates/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalTemplateEditorScreen
              accountId={accountId}
              access={access}
              approvals={approvals}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              creating={true}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/approvals/templates/:templateId/edit"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalTemplateEditorScreen
              accountId={accountId}
              access={access}
              approvals={approvals}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              creating={false}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/approvals"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalInboxScreen
              accountId={accountId}
              access={access}
              approvals={approvals}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/approvals/:approvalId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalRequestScreen
              accountId={accountId}
              access={access}
              approvals={approvals}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/imports/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmployeeImportCreationScreen
              accountId={accountId}
              access={access}
              people={people}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      {(["apply", "resume", "cancel"] as const).map((action) => (
        <Route
          key={action}
          path={`/people/imports/:importId/${action}`}
          element={
            <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
              <EmployeeImportTransitionScreen
                accountId={accountId}
                access={access}
                people={people}
                action={action}
                companyName={company.name}
                timezone={company.timezone}
                locale={locale}
                nextIdentifier={nextIdentifier}
              />
            </Suspense>
          }
        />
      ))}
      <Route
        path="/people/imports"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmployeeImportsScreen
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
        path="/people/imports/:importId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmployeeImportScreen
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
        path="/people/employees/:employeeId/lifecycle/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LifecycleCaseCreationScreen
              accountId={accountId}
              access={access}
              loadEmployee={people.loadEmployee}
              lifecycle={lifecycle}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/cases"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LifecycleCasesScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/cases/:caseId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LifecycleCaseScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/cases/:caseId/offboarding"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OffboardingScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/tasks"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <AssignedLifecycleTasksScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/templates"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LifecycleTemplatesScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/templates/:templateId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LifecycleTemplateScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/templates/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LifecycleTemplateEditorScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
              creating={true}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/templates/:templateId/edit"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LifecycleTemplateEditorScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
              creating={false}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/settings/client-policy/edit"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ClientPolicyEditorScreen
              accountId={accountId}
              access={access}
              administration={administration}
              companyName={company.name}
              locale={locale}
              clientBuild={clientBuild}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route path="/" element={<OverviewPage company={company} locale={locale} />} />
      <Route
        path="/people/employees/:employeeId/revisions/:revision/cancel"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmploymentCancellationScreen
              accountId={accountId}
              access={access}
              people={people}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/employees/:employeeId/employment/edit"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmploymentEditorScreen
              accountId={accountId}
              access={access}
              people={people}
              loadUnits={organization.loadUnits}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/employees/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmployeeCreationScreen
              accountId={accountId}
              access={access}
              people={people}
              loadUnits={organization.loadUnits}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      {[
        { path: "/people/employees/:employeeId/profile", mode: "read" as const },
        { path: "/people/employees/:employeeId/profile/edit", mode: "edit" as const },
      ].map(({ path, mode }) => (
        <Route
          key={path}
          path={path}
          element={
            <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
              <PersonProfileScreen
                accountId={accountId}
                access={access}
                people={people}
                companies={companies}
                companyName={company.name}
                timezone={company.timezone}
                locale={locale}
                mode={mode}
                nextIdentifier={nextIdentifier}
              />
            </Suspense>
          }
        />
      ))}
      {[
        { path: "/organization/units/new", creating: true },
        { path: "/organization/units/:unitId/edit", creating: false },
      ].map(({ path, creating }) => (
        <Route
          key={path}
          path={path}
          element={
            <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
              <OrganizationEditorScreen
                accountId={accountId}
                access={access}
                organization={organization}
                companyName={company.name}
                timezone={company.timezone}
                locale={locale}
                creating={creating}
                nextIdentifier={nextIdentifier}
              />
            </Suspense>
          }
        />
      ))}
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
