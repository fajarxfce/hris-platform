import { Home20Regular } from "@fluentui/react-icons";
import type { AppNavigationSection } from "../../core/presentation/components/app-portal-shell";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import { administrationNavigation } from "../../features/administration/presentation/navigation/administration-navigation";
import { approvalsNavigation } from "../../features/approvals/presentation/navigation/approvals-navigation";
import { communicationsNavigation } from "../../features/communications/presentation/navigation/communications-navigation";
import {
  documentRetentionNavigation,
  documentsNavigation,
} from "../../features/documents/presentation/navigation/documents-navigation";
import { expenseNavigation } from "../../features/expenses/presentation/navigation/expense-navigation";
import type { CompanyAccess } from "../../features/identity/domain/entities/session";
import { identityNavigation } from "../../features/identity/presentation/navigation/identity-navigation";
import { jobsNavigation } from "../../features/jobs/presentation/navigation/jobs-navigation";
import { leaveNavigation } from "../../features/leave/presentation/navigation/leave-navigation";
import { lifecycleNavigation } from "../../features/lifecycle/presentation/navigation/lifecycle-navigation";
import { organizationNavigation } from "../../features/organization/presentation/navigation/organization-navigation";
import { peopleNavigation } from "../../features/people/presentation/navigation/people-navigation";
import { reportingNavigation } from "../../features/reporting/presentation/navigation/reporting-navigation";
import { workforceNavigation } from "../../features/workforce/presentation/navigation/workforce-navigation";

/** Features own visibility and destinations; the application composes their sections. */
export function portalNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationSection[] {
  const text = messages(locale);
  return [
    {
      id: "overview",
      label: null,
      items: [{ to: "/", label: text.overview, icon: <Home20Regular /> }],
    },
    {
      id: "people",
      label: text.navigationPeople,
      items: [
        ...peopleNavigation(access, locale),
        ...organizationNavigation(access, locale),
        ...lifecycleNavigation(access, locale),
        ...documentsNavigation(access, locale),
      ],
    },
    { id: "workforce", label: text.navigationWork, items: workforceNavigation(access, locale) },
    {
      id: "requests",
      label: text.navigationRequests,
      items: [
        ...leaveNavigation(access, locale),
        ...expenseNavigation(access, locale),
        ...approvalsNavigation(access, locale),
      ],
    },
    {
      id: "communications",
      label: text.navigationCommunications,
      items: communicationsNavigation(access, locale),
    },
    {
      id: "administration",
      label: text.navigationAdministration,
      items: [
        ...reportingNavigation(access, locale),
        ...identityNavigation(access, locale),
        ...administrationNavigation(access, locale),
        ...documentRetentionNavigation(access, locale),
        ...jobsNavigation(access, locale),
      ],
    },
  ].filter((section) => section.items.length > 0);
}
