import { lazy, Suspense } from "react";
import { Link, Route, Routes } from "react-router-dom";
import type { AccountId } from "../../core/domain/identifiers";
import { AppLoading } from "../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import type { AdministrationUseCases } from "../../features/administration/presentation/contracts/administration-use-cases";
import type { ApprovalsUseCases } from "../../features/approvals/presentation/contracts/approvals-use-cases";
import type { CommunicationsUseCases } from "../../features/communications/presentation/contracts/communications-use-cases";
import type {
  CompanyAccess,
  CompanyMembership,
} from "../../features/identity/domain/entities/session";
import type { IdentityAdministrationUseCases } from "../../features/identity/presentation/contracts/identity-administration-use-cases";
import type { JobsUseCases } from "../../features/jobs/presentation/contracts/jobs-use-cases";
import type { LeaveUseCases } from "../../features/leave/presentation/contracts/leave-use-cases";
import type { LifecycleUseCases } from "../../features/lifecycle/presentation/contracts/lifecycle-use-cases";
import type { OrganizationUseCases } from "../../features/organization/presentation/contracts/organization-use-cases";
import { OverviewPage } from "../../features/overview/presentation/pages/overview-page";
import type { PeopleUseCases } from "../../features/people/presentation/contracts/people-use-cases";
import type { ReportingUseCases } from "../../features/reporting/presentation/contracts/reporting-use-cases";
import { approvalResourceRoute } from "./approval-resource-route";

const CommunicationsRoutes = lazy(() =>
  import("../../features/communications/presentation/navigation/communications-routes").then(
    (module) => ({
      default: module.CommunicationsRoutes,
    }),
  ),
);

const CompanyMembersScreen = lazy(() =>
  import("../../features/identity/presentation/bindings/company-members-screen").then((module) => ({
    default: module.CompanyMembersScreen,
  })),
);
const CompanyMemberScreen = lazy(() =>
  import("../../features/identity/presentation/bindings/company-member-screen").then((module) => ({
    default: module.CompanyMemberScreen,
  })),
);

const PersonAccountBindingScreen = lazy(() =>
  import("../../features/people/presentation/bindings/person-account-binding-screen").then(
    (module) => ({ default: module.PersonAccountBindingScreen }),
  ),
);

const LeaveBalancesScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-balances-screen").then((module) => ({
    default: module.LeaveBalancesScreen,
  })),
);
const LeaveAdjustmentCatalogScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-adjustment-catalog-screen").then(
    (module) => ({
      default: module.LeaveAdjustmentCatalogScreen,
    }),
  ),
);
const LeaveBalanceAdjustmentScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-balance-adjustment-screen").then(
    (module) => ({
      default: module.LeaveBalanceAdjustmentScreen,
    }),
  ),
);
const LeaveLedgerScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-ledger-screen").then((module) => ({
    default: module.LeaveLedgerScreen,
  })),
);

const LeavePoliciesScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-policies-screen").then((module) => ({
    default: module.LeavePoliciesScreen,
  })),
);
const LeavePolicyScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-policy-screen").then((module) => ({
    default: module.LeavePolicyScreen,
  })),
);
const LeavePolicyEditorScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-policy-editor-screen").then(
    (module) => ({ default: module.LeavePolicyEditorScreen }),
  ),
);

const LeaveRequestsScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-requests-screen").then((module) => ({
    default: module.LeaveRequestsScreen,
  })),
);
const LeaveActionScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-action-screen").then((module) => ({
    default: module.LeaveActionScreen,
  })),
);
const LeaveRequestScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-request-screen").then((module) => ({
    default: module.LeaveRequestScreen,
  })),
);

const ApprovalDelegationsScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-delegations-screen").then(
    (module) => ({ default: module.ApprovalDelegationsScreen }),
  ),
);
const ApprovalDelegationScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-delegation-screen").then(
    (module) => ({ default: module.ApprovalDelegationScreen }),
  ),
);
const ApprovalDelegationEditorScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-delegation-editor-screen").then(
    (module) => ({ default: module.ApprovalDelegationEditorScreen }),
  ),
);

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

const ApprovalReassignmentScreen = lazy(() =>
  import("../../features/approvals/presentation/bindings/approval-reassignment-screen").then(
    (module) => ({ default: module.ApprovalReassignmentScreen }),
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
  identityAdministration,
  company,
  companies,
  access,
  reporting,
  administration,
  approvals,
  communications,
  jobs,
  leave,
  organization,
  people,
  lifecycle,
  locale,
  nextIdentifier,
}: {
  clientBuild: number;
  accountId: AccountId;
  identityAdministration: IdentityAdministrationUseCases;
  company: CompanyMembership | null;
  companies: readonly CompanyMembership[];
  access: CompanyAccess | null;
  reporting: ReportingUseCases;
  administration: AdministrationUseCases;
  approvals: ApprovalsUseCases;
  communications: CommunicationsUseCases;
  jobs: JobsUseCases;
  leave: LeaveUseCases;
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
        path="/communications/*"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <CommunicationsRoutes
              accountId={accountId}
              access={access}
              communications={communications}
              nextIdentifier={nextIdentifier}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/employees/:employeeId/account-link"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <PersonAccountBindingScreen
              accountId={accountId}
              access={access}
              people={people}
              loadMembers={identityAdministration.loadCompanyMembers}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/administration/members"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <CompanyMembersScreen
              accountId={accountId}
              access={access}
              actions={identityAdministration}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/administration/members/:memberId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <CompanyMemberScreen
              accountId={accountId}
              access={access}
              actions={identityAdministration}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/employees/:employeeId/balances/adjust"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveAdjustmentCatalogScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/employees/:employeeId/balances/:typeId/adjust"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveBalanceAdjustmentScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/employees/:employeeId/balances"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveBalancesScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/employees/:employeeId/balances/:typeId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveLedgerScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/policies/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeavePolicyEditorScreen
              accountId={accountId}
              access={access}
              leave={leave}
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
        path="/leave/policies/:policyId/edit"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeavePolicyEditorScreen
              accountId={accountId}
              access={access}
              leave={leave}
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
        path="/leave/policies"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeavePoliciesScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/policies/:policyId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeavePolicyScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              locale={locale}
              timezone={company.timezone}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/requests/:requestId/:intent"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveActionScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/requests"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveRequestsScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/requests/:requestId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveRequestScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/approvals/:approvalId/reassign"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalReassignmentScreen
              accountId={accountId}
              access={access}
              approvals={approvals}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/approvals/delegations"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalDelegationsScreen
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
        path="/approvals/delegations/:delegationId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalDelegationScreen
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
        path="/approvals/delegations/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalDelegationEditorScreen
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
        path="/approvals/delegations/:delegationId/edit"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ApprovalDelegationEditorScreen
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
              resourceRoute={approvalResourceRoute}
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
