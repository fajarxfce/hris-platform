import { lazy, Suspense, useMemo } from "react";
import { Link, Route, Routes, useMatch } from "react-router-dom";
import type { AccountId } from "../../core/domain/identifiers";
import { AppLoading } from "../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import type { AdministrationUseCases } from "../../features/administration/presentation/contracts/administration-use-cases";
import type { ApprovalsUseCases } from "../../features/approvals/presentation/contracts/approvals-use-cases";
import type { CommunicationsUseCases } from "../../features/communications/presentation/contracts/communications-use-cases";
import type { DocumentsUseCases } from "../../features/documents/presentation/contracts/documents-use-cases";
import type { ExpenseUseCases } from "../../features/expenses/presentation/contracts/expense-use-cases";
import type {
  CompanyAccess,
  CompanyMembership,
} from "../../features/identity/domain/entities/session";
import type { DeviceSessionUseCases } from "../../features/identity/presentation/contracts/device-session-use-cases";
import type { IdentityAdministrationUseCases } from "../../features/identity/presentation/contracts/identity-administration-use-cases";
import type { JobsUseCases } from "../../features/jobs/presentation/contracts/jobs-use-cases";
import type { LeaveUseCases } from "../../features/leave/presentation/contracts/leave-use-cases";
import type { LifecycleUseCases } from "../../features/lifecycle/presentation/contracts/lifecycle-use-cases";
import type { OrganizationUseCases } from "../../features/organization/presentation/contracts/organization-use-cases";
import { OverviewPage } from "../../features/overview/presentation/pages/overview-page";
import type { PayrollUseCases } from "../../features/payroll/presentation/contracts/payroll-use-cases";
import type { PeopleUseCases } from "../../features/people/presentation/contracts/people-use-cases";
import type { ReportingUseCases } from "../../features/reporting/presentation/contracts/reporting-use-cases";
import type { WorkforceUseCases } from "../../features/workforce/presentation/contracts/workforce-use-cases";
import { approvalResourceRoute } from "./approval-resource-route";

const noPlatformPermissions: readonly string[] = Object.freeze([]);
const IntegrationConfigurationScreen = lazy(() =>
  import(
    "../../features/administration/presentation/bindings/integration-configuration-screen"
  ).then((module) => ({ default: module.IntegrationConfigurationScreen })),
);
const DeviceSessionsScreen = lazy(() =>
  import("../../features/identity/presentation/bindings/device-sessions-screen").then((module) => ({
    default: module.DeviceSessionsScreen,
  })),
);
const PlatformAccountRoutes = lazy(() =>
  import("../../features/identity/presentation/navigation/platform-account-routes").then(
    (module) => ({ default: module.PlatformAccountRoutes }),
  ),
);

const CommunicationsRoutes = lazy(() =>
  import("../../features/communications/presentation/navigation/communications-routes").then(
    (module) => ({
      default: module.CommunicationsRoutes,
    }),
  ),
);

const RoleTemplateRoutes = lazy(() =>
  import("../../features/identity/presentation/navigation/role-template-routes").then((module) => ({
    default: module.RoleTemplateRoutes,
  })),
);

const CompanyMemberRoutes = lazy(() =>
  import("../../features/identity/presentation/navigation/company-member-routes").then(
    (module) => ({
      default: module.CompanyMemberRoutes,
    }),
  ),
);

const PersonAccountBindingScreen = lazy(() =>
  import("../../features/people/presentation/bindings/person-account-binding-screen").then(
    (module) => ({ default: module.PersonAccountBindingScreen }),
  ),
);

const LeaveCalendarScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-calendar-screen").then((module) => ({
    default: module.LeaveCalendarScreen,
  })),
);

const LeaveAccrualSchedulesScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-accrual-schedules-screen").then(
    (module) => ({ default: module.LeaveAccrualSchedulesScreen }),
  ),
);
const LeaveAccrualScheduleScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-accrual-schedule-screen").then(
    (module) => ({ default: module.LeaveAccrualScheduleScreen }),
  ),
);
const LeaveAccrualScheduleStartScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-accrual-schedule-start-screen").then(
    (module) => ({ default: module.LeaveAccrualScheduleStartScreen }),
  ),
);
const LeaveCarryExpiriesScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-carry-expiries-screen").then(
    (module) => ({ default: module.LeaveCarryExpiriesScreen }),
  ),
);
const LeaveCarryExpiryScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-carry-expiry-screen").then((module) => ({
    default: module.LeaveCarryExpiryScreen,
  })),
);
const LeaveCarryExpiryResumeScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-carry-expiry-resume-screen").then(
    (module) => ({ default: module.LeaveCarryExpiryResumeScreen }),
  ),
);
const LeaveBatchesScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-batches-screen").then((module) => ({
    default: module.LeaveBatchesScreen,
  })),
);
const LeaveBatchStartScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-batch-start-screen").then((module) => ({
    default: module.LeaveBatchStartScreen,
  })),
);
const LeaveBatchScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-batch-screen").then((module) => ({
    default: module.LeaveBatchScreen,
  })),
);
const LeaveBatchResumeScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-batch-resume-screen").then((module) => ({
    default: module.LeaveBatchResumeScreen,
  })),
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
const LeaveCreationScreen = lazy(() =>
  import("../../features/leave/presentation/bindings/leave-creation-screen").then((module) => ({
    default: module.LeaveCreationScreen,
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
const OnboardingSchedulesScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/onboarding-schedules-screen").then(
    (module) => ({ default: module.OnboardingSchedulesScreen }),
  ),
);
const OnboardingScheduleStartScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/onboarding-schedule-start-screen").then(
    (module) => ({ default: module.OnboardingScheduleStartScreen }),
  ),
);
const OnboardingScheduleScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/onboarding-schedule-screen").then(
    (module) => ({ default: module.OnboardingScheduleScreen }),
  ),
);
const OnboardingScheduleChangeScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/onboarding-schedule-change-screen").then(
    (module) => ({ default: module.OnboardingScheduleChangeScreen }),
  ),
);
const OffboardingSchedulesScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/offboarding-schedules-screen").then(
    (module) => ({ default: module.OffboardingSchedulesScreen }),
  ),
);
const OffboardingScheduleStartScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/offboarding-schedule-start-screen").then(
    (module) => ({ default: module.OffboardingScheduleStartScreen }),
  ),
);
const OffboardingScheduleScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/offboarding-schedule-screen").then(
    (module) => ({ default: module.OffboardingScheduleScreen }),
  ),
);
const OffboardingScheduleChangeScreen = lazy(() =>
  import("../../features/lifecycle/presentation/bindings/offboarding-schedule-change-screen").then(
    (module) => ({ default: module.OffboardingScheduleChangeScreen }),
  ),
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
const EmploymentTransferScreen = lazy(() =>
  import("../../features/people/presentation/bindings/employment-transfer-screen").then(
    (module) => ({
      default: module.EmploymentTransferScreen,
    }),
  ),
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

const ReportExportHistoryScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/report-export-history-screen").then(
    (module) => ({ default: module.ReportExportHistoryScreen }),
  ),
);
const ReportExportScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/report-export-screen").then((module) => ({
    default: module.ReportExportScreen,
  })),
);
const SavedReportFiltersScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/saved-report-filters-screen").then(
    (module) => ({ default: module.SavedReportFiltersScreen }),
  ),
);
const ReportFilterEditorScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/report-filter-editor-screen").then(
    (module) => ({ default: module.ReportFilterEditorScreen }),
  ),
);
const ReportCatalogueScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/report-catalogue-screen").then(
    (module) => ({ default: module.ReportCatalogueScreen }),
  ),
);
const HeadcountScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/headcount-screen").then((module) => ({
    default: module.HeadcountScreen,
  })),
);

const ExpenseReportScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/expense-report-screen").then((module) => ({
    default: module.ExpenseReportScreen,
  })),
);
const ExpenseReportClaimsScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/expense-report-claims-screen").then(
    (module) => ({ default: module.ExpenseReportClaimsScreen }),
  ),
);

const LeaveReportScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/leave-report-screen").then((module) => ({
    default: module.LeaveReportScreen,
  })),
);
const LeaveReportRequestsScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/leave-report-requests-screen").then(
    (module) => ({ default: module.LeaveReportRequestsScreen }),
  ),
);

const OvertimeReportScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/overtime-report-screen").then(
    (module) => ({ default: module.OvertimeReportScreen }),
  ),
);
const AttendanceClosingReportScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/attendance-closing-report-screen").then(
    (module) => ({ default: module.AttendanceClosingReportScreen }),
  ),
);
const AttendanceClosingEmployeesScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/attendance-closing-employees-screen").then(
    (module) => ({ default: module.AttendanceClosingEmployeesScreen }),
  ),
);
const PayrollCostReportScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/payroll-cost-report-screen").then(
    (module) => ({ default: module.PayrollCostReportScreen }),
  ),
);
const AttendanceReportScreen = lazy(() =>
  import("../../features/reporting/presentation/bindings/attendance-report-screen").then(
    (module) => ({ default: module.AttendanceReportScreen }),
  ),
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

const WorkforceRoutes = lazy(() =>
  import("../../features/workforce/presentation/navigation/workforce-routes").then((module) => ({
    default: module.WorkforceRoutes,
  })),
);

const DocumentsRoutes = lazy(() =>
  import("../../features/documents/presentation/navigation/documents-routes").then((module) => ({
    default: module.DocumentsRoutes,
  })),
);

const ExpenseRoutes = lazy(() =>
  import("../../features/expenses/presentation/navigation/expense-routes").then((module) => ({
    default: module.ExpenseRoutes,
  })),
);

const PayrollRoutes = lazy(() =>
  import("../../features/payroll/presentation/navigation/payroll-routes").then((module) => ({
    default: module.PayrollRoutes,
  })),
);

export function PortalRoutes({
  clientBuild,
  accountId,
  platformPermissions = noPlatformPermissions,
  identityAdministration,
  deviceSessions,
  company,
  companies,
  access,
  reporting,
  administration,
  approvals,
  communications,
  documents,
  jobs,
  leave,
  expense,
  payroll,
  organization,
  workforce,
  people,
  lifecycle,
  locale,
  nextIdentifier,
}: {
  clientBuild: number;
  accountId: AccountId;
  platformPermissions?: readonly string[];
  identityAdministration: IdentityAdministrationUseCases;
  deviceSessions: DeviceSessionUseCases;
  company: CompanyMembership | null;
  companies: readonly CompanyMembership[];
  access: CompanyAccess | null;
  reporting: ReportingUseCases;
  administration: AdministrationUseCases;
  approvals: ApprovalsUseCases;
  communications: CommunicationsUseCases;
  documents: DocumentsUseCases;
  jobs: JobsUseCases;
  leave: LeaveUseCases;
  expense: ExpenseUseCases;
  payroll: PayrollUseCases;
  organization: OrganizationUseCases;
  workforce: WorkforceUseCases;
  people: PeopleUseCases;
  lifecycle: LifecycleUseCases;
  locale: Locale;
  nextIdentifier: () => string;
}) {
  const platform = useMatch("/platform/*");
  const integrations = useMatch("/platform/integrations");
  const ownSessions = useMatch("/account/sessions");
  const platformAccess = useMemo(
    () => Object.freeze({ accountId, permissions: platformPermissions }),
    [accountId, platformPermissions],
  );
  if (integrations)
    return (
      <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
        <IntegrationConfigurationScreen
          access={platformAccess}
          load={administration.loadIntegrationConfiguration}
          loadWorkers={administration.loadWorkerIntegrations}
          locale={locale}
        />
      </Suspense>
    );
  if (platform)
    return (
      <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
        <PlatformAccountRoutes
          access={platformAccess}
          actions={identityAdministration}
          locale={locale}
          nextIdentifier={nextIdentifier}
        />
      </Suspense>
    );
  if (ownSessions)
    return (
      <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
        <DeviceSessionsScreen accountId={accountId} actions={deviceSessions} locale={locale} />
      </Suspense>
    );
  if (!company || !access) return <OverviewPage company={null} locale={locale} />;
  return (
    <Routes>
      <Route
        path="/payroll/*"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <PayrollRoutes
              accountId={accountId}
              access={access}
              payroll={payroll}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/expenses/*"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ExpenseRoutes
              accountId={accountId}
              access={access}
              expense={expense}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/documents/*"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <DocumentsRoutes
              accountId={accountId}
              access={access}
              documents={documents}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/workforce/*"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <WorkforceRoutes
              accountId={accountId}
              access={access}
              workforce={workforce}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
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
        path="/administration/roles/*"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <RoleTemplateRoutes
              accountId={accountId}
              access={access}
              actions={identityAdministration}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/administration/members/*"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <CompanyMemberRoutes
              accountId={accountId}
              access={access}
              platform={platformAccess}
              actions={identityAdministration}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/calendar"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveCalendarScreen
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
        path="/leave/accrual-schedules"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveAccrualSchedulesScreen
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
        path="/leave/accrual-schedules/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveAccrualScheduleStartScreen
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
        path="/leave/accrual-schedules/:scheduleId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveAccrualScheduleScreen
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
        path="/leave/carry-expiries"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveCarryExpiriesScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/carry-expiries/:expiryId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveCarryExpiryScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/carry-expiries/:expiryId/resume"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveCarryExpiryResumeScreen
              accountId={accountId}
              access={access}
              leave={leave}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/leave/batches"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveBatchesScreen
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
        path="/leave/batches/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveBatchStartScreen
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
        path="/leave/batches/:batchId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveBatchScreen
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
        path="/leave/batches/:batchId/resume"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveBatchResumeScreen
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
        path="/leave/requests/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveCreationScreen
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
        path="/people/employees/:employeeId/onboarding-schedules"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OnboardingSchedulesScreen
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
        path="/people/employees/:employeeId/onboarding-schedules/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OnboardingScheduleStartScreen
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
        path="/people/employees/:employeeId/onboarding-schedules/:scheduleId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OnboardingScheduleScreen
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
        path="/people/employees/:employeeId/onboarding-schedules/:scheduleId/cancel"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OnboardingScheduleChangeScreen
              accountId={accountId}
              access={access}
              loadEmployee={people.loadEmployee}
              lifecycle={lifecycle}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
              action="cancel"
            />
          </Suspense>
        }
      />
      <Route
        path="/people/employees/:employeeId/onboarding-schedules/:scheduleId/recover"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OnboardingScheduleChangeScreen
              accountId={accountId}
              access={access}
              loadEmployee={people.loadEmployee}
              lifecycle={lifecycle}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
              action="recover"
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
        path="/people/lifecycle/cases/:caseId/offboarding-schedules"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OffboardingSchedulesScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/cases/:caseId/offboarding-schedules/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OffboardingScheduleStartScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/cases/:caseId/offboarding-schedules/:scheduleId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OffboardingScheduleScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/cases/:caseId/offboarding-schedules/:scheduleId/cancel"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OffboardingScheduleChangeScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
              action="cancel"
            />
          </Suspense>
        }
      />
      <Route
        path="/people/lifecycle/cases/:caseId/offboarding-schedules/:scheduleId/recover"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OffboardingScheduleChangeScreen
              accountId={accountId}
              access={access}
              lifecycle={lifecycle}
              companyName={company.name}
              locale={locale}
              nextIdentifier={nextIdentifier}
              action="recover"
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
        path="/people/employees/:employeeId/transfer"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <EmploymentTransferScreen
              accountId={accountId}
              access={access}
              people={people}
              companies={companies}
              loadUnits={organization.loadUnits}
              companyName={company.name}
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
              companies={companies}
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
        path="/reports/exports"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ReportExportHistoryScreen
              accountId={accountId}
              access={access}
              actions={reporting}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/exports/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ReportExportScreen
              accountId={accountId}
              access={access}
              actions={reporting}
              companyName={company.name}
              companies={companies}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
              creating={true}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/exports/:exportId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ReportExportScreen
              accountId={accountId}
              access={access}
              actions={reporting}
              companyName={company.name}
              companies={companies}
              timezone={company.timezone}
              locale={locale}
              nextIdentifier={nextIdentifier}
              creating={false}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/saved-filters"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <SavedReportFiltersScreen
              accountId={accountId}
              access={access}
              actions={reporting}
              companyName={company.name}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/saved-filters/new"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ReportFilterEditorScreen
              accountId={accountId}
              access={access}
              actions={reporting}
              companyName={company.name}
              locale={locale}
              companies={companies}
              timezone={company.timezone}
              nextIdentifier={nextIdentifier}
              creating={true}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/saved-filters/:filterId"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ReportFilterEditorScreen
              accountId={accountId}
              access={access}
              actions={reporting}
              companyName={company.name}
              locale={locale}
              companies={companies}
              timezone={company.timezone}
              nextIdentifier={nextIdentifier}
              creating={false}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ReportCatalogueScreen
              accountId={accountId}
              access={access}
              loadCatalogue={reporting.loadCatalogue}
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
        path="/reports/expenses"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ExpenseReportScreen
              accountId={accountId}
              access={access}
              loadExpenses={reporting.loadExpenses}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/expenses/claims"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <ExpenseReportClaimsScreen
              accountId={accountId}
              access={access}
              loadExpenseClaims={reporting.loadExpenseClaims}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/leave"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveReportScreen
              accountId={accountId}
              access={access}
              loadLeave={reporting.loadLeave}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/leave/requests"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <LeaveReportRequestsScreen
              accountId={accountId}
              access={access}
              loadLeaveRequests={reporting.loadLeaveRequests}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/overtime"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <OvertimeReportScreen
              accountId={accountId}
              access={access}
              loadOvertime={reporting.loadOvertime}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/attendance-closings"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <AttendanceClosingReportScreen
              accountId={accountId}
              access={access}
              loadAttendanceClosings={reporting.loadAttendanceClosings}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/attendance-closings/employees"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <AttendanceClosingEmployeesScreen
              accountId={accountId}
              access={access}
              loadAttendanceClosingEmployees={reporting.loadAttendanceClosingEmployees}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/payroll-costs"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <PayrollCostReportScreen
              accountId={accountId}
              access={access}
              loadPayrollCosts={reporting.loadPayrollCosts}
              companyName={company.name}
              timezone={company.timezone}
              locale={locale}
            />
          </Suspense>
        }
      />
      <Route
        path="/reports/attendance-exceptions"
        element={
          <Suspense fallback={<AppLoading label={messages(locale).loading} />}>
            <AttendanceReportScreen
              accountId={accountId}
              access={access}
              loadAttendanceExceptions={reporting.loadAttendanceExceptions}
              companyName={company.name}
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
