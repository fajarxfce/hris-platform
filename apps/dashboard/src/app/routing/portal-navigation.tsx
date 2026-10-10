import {
  ApprovalsApp20Regular,
  CalendarCheckmark20Regular,
  ClipboardTaskListLtr20Regular,
  DataBarVertical20Regular,
  History20Regular,
  Home20Regular,
  Organization20Regular,
  People20Regular,
  PeopleSwap20Regular,
  Settings20Regular,
  TaskListSquareLtr20Regular,
} from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../core/presentation/components/app-portal-shell";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import { canReadAudit } from "../../features/administration/domain/policies/audit-search-policy";
import { canReadClientSettings } from "../../features/administration/domain/policies/client-settings-policy";
import { administrationMessages } from "../../features/administration/presentation/i18n/administration-messages";
import { clientPolicyMessages } from "../../features/administration/presentation/i18n/client-policy-messages";
import {
  canManageApprovals,
  canReadApprovalInbox,
} from "../../features/approvals/domain/policies/approval-read-policy";
import { approvalDelegationMessages } from "../../features/approvals/presentation/i18n/approval-delegation-messages";
import { approvalMessages } from "../../features/approvals/presentation/i18n/approval-messages";
import { approvalTemplateMessages } from "../../features/approvals/presentation/i18n/approval-template-messages";
import type { CompanyAccess } from "../../features/identity/domain/entities/session";
import { canManageCompanyMembers } from "../../features/identity/domain/policies/company-member-policy";
import { companyMemberMessages } from "../../features/identity/presentation/i18n/company-member-messages";
import { jobMessages } from "../../features/jobs/presentation/i18n/job-messages";
import {
  canBrowseCompanyLeave,
  canManageLeavePolicies,
} from "../../features/leave/domain/policies/leave-read-policy";
import { leaveMessages } from "../../features/leave/presentation/i18n/leave-messages";
import { leavePolicyMessages } from "../../features/leave/presentation/i18n/leave-policy-messages";
import { canReadAssignedLifecycle } from "../../features/lifecycle/domain/policies/lifecycle-case-policy";
import {
  canManageLifecycle,
  canReadLifecycle,
} from "../../features/lifecycle/domain/policies/lifecycle-template-policy";
import { lifecycleCaseMessages } from "../../features/lifecycle/presentation/i18n/lifecycle-case-messages";
import { lifecycleMessages } from "../../features/lifecycle/presentation/i18n/lifecycle-messages";
import { canReadOrganization } from "../../features/organization/domain/policies/organization-unit-policy";
import { organizationMessages } from "../../features/organization/presentation/i18n/organization-messages";
import { canCreateEmployee } from "../../features/people/domain/policies/employee-creation-policy";
import { canImportEmployees } from "../../features/people/domain/policies/employee-import-policy";
import { canReadEmployees } from "../../features/people/domain/policies/employee-policy";
import { employeeCreationMessages } from "../../features/people/presentation/i18n/employee-creation-messages";
import { employeeImportMessages } from "../../features/people/presentation/i18n/employee-import-messages";
import { canReadHeadcount } from "../../features/reporting/domain/policies/headcount-policy";

/** Navigation visibility uses the same client policy as the feature; the API enforces access. */
export function portalNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const text = messages(locale);
  const permissions = access?.permissions ?? [];
  return [
    { to: "/", label: text.overview, icon: <Home20Regular /> },
    ...(canManageCompanyMembers(permissions)
      ? [
          {
            to: "/administration/members",
            label: companyMemberMessages(locale).title,
            icon: <People20Regular />,
          },
        ]
      : []),
    ...(canManageLeavePolicies(permissions)
      ? [
          {
            to: "/leave/policies",
            label: leavePolicyMessages(locale).title,
            icon: <ClipboardTaskListLtr20Regular />,
          },
        ]
      : []),
    ...(canBrowseCompanyLeave(permissions)
      ? [
          {
            to: "/leave/requests",
            label: leaveMessages(locale).requests,
            icon: <CalendarCheckmark20Regular />,
          },
        ]
      : []),
    ...(canReadApprovalInbox(permissions)
      ? [
          {
            to: "/approvals",
            label: approvalMessages(locale).title,
            icon: <ApprovalsApp20Regular />,
          },
        ]
      : []),
    ...(canReadApprovalInbox(permissions)
      ? [
          {
            to: "/approvals/delegations",
            label: approvalDelegationMessages(locale).title,
            icon: <PeopleSwap20Regular />,
          },
        ]
      : []),
    ...(canManageApprovals(permissions)
      ? [
          {
            to: "/approvals/templates",
            label: approvalTemplateMessages(locale).title,
            icon: <ClipboardTaskListLtr20Regular />,
          },
        ]
      : []),
    ...(canReadOrganization(permissions)
      ? [
          {
            to: "/organization/units",
            label: organizationMessages(locale).title,
            icon: <Organization20Regular />,
          },
        ]
      : []),
    ...(canReadEmployees(permissions)
      ? [{ to: "/people/employees", label: text.people, icon: <People20Regular /> }]
      : canCreateEmployee(permissions)
        ? [
            {
              to: "/people/employees/new",
              label: employeeCreationMessages(locale).create,
              icon: <People20Regular />,
            },
          ]
        : []),
    ...(canImportEmployees(permissions)
      ? [
          {
            to: "/people/imports",
            label: employeeImportMessages(locale).title,
            icon: <ClipboardTaskListLtr20Regular />,
          },
        ]
      : []),
    ...(canReadLifecycle(permissions)
      ? [
          {
            to: "/people/lifecycle/cases",
            label: lifecycleCaseMessages(locale).title,
            icon: <People20Regular />,
          },
        ]
      : []),
    ...(canReadAssignedLifecycle(permissions)
      ? [
          {
            to: "/people/lifecycle/tasks",
            label: lifecycleCaseMessages(locale).queue,
            icon: <TaskListSquareLtr20Regular />,
          },
        ]
      : []),
    ...(canReadLifecycle(permissions)
      ? [
          {
            to: "/people/lifecycle/templates",
            label: lifecycleMessages(locale).title,
            icon: <ClipboardTaskListLtr20Regular />,
          },
        ]
      : canManageLifecycle(permissions)
        ? [
            {
              to: "/people/lifecycle/templates/new",
              label: lifecycleMessages(locale).create,
              icon: <ClipboardTaskListLtr20Regular />,
            },
          ]
        : []),
    ...(access
      ? [
          {
            to: "/administration/jobs",
            label: jobMessages(locale).title,
            icon: <TaskListSquareLtr20Regular />,
          },
        ]
      : []),
    ...(canReadHeadcount(permissions)
      ? [{ to: "/reports/headcount", label: text.reports, icon: <DataBarVertical20Regular /> }]
      : []),
    ...(canReadAudit(permissions)
      ? [
          {
            to: "/administration/audit",
            label: administrationMessages(locale).audit,
            icon: <History20Regular />,
          },
        ]
      : []),
    ...(canReadClientSettings(permissions)
      ? [
          {
            to: "/settings/client-policy",
            label: clientPolicyMessages(locale).title,
            icon: <Settings20Regular />,
          },
        ]
      : []),
  ];
}
