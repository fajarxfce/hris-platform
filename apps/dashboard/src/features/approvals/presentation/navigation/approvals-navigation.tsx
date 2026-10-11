import {
  ApprovalsApp20Regular,
  ClipboardTaskListLtr20Regular,
  PeopleSwap20Regular,
} from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  canManageApprovals,
  canReadApprovalInbox,
} from "../../domain/policies/approval-read-policy";
import { approvalDelegationMessages } from "../i18n/approval-delegation-messages";
import { approvalMessages } from "../i18n/approval-messages";
import { approvalTemplateMessages } from "../i18n/approval-template-messages";

export function approvalsNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  return [
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
  ];
}
