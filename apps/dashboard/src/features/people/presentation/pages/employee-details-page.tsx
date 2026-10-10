import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { employmentMessages } from "../i18n/employment-messages";
import { peopleMessages } from "../i18n/people-messages";
import { personProfileMessages } from "../i18n/person-profile-messages";
import type { EmployeeDetailsState } from "../models/employee-details-state";
import type { employeeDetailsView } from "../models/employee-view";

export function EmployeeDetailsPage({
  state,
  view,
  companyName,
  locale,
  backTo,
  profileTo,
  editEmploymentTo,
  tab,
  canReadHistory,
  history,
  onTab,
  onRefresh,
}: {
  state: EmployeeDetailsState;
  view: ReturnType<typeof employeeDetailsView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  profileTo: string | null;
  editEmploymentTo: string | null;
  tab: "overview" | "history";
  canReadHistory: boolean;
  history: ReactNode;
  onTab: (tab: string) => void;
  onRefresh: () => void;
}) {
  const text = peopleMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.employee?.legalName ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {editEmploymentTo && state.employee && (
              <Link to={editEmploymentTo}>{employmentMessages(locale).edit}</Link>
            )}
            {profileTo && state.employee && (
              <Link to={profileTo}>{personProfileMessages(locale).title}</Link>
            )}
            {(tab === "overview" || state.employee === null) && (
              <AppButton
                icon={<ArrowClockwise20Regular />}
                disabled={state.stage === "loading"}
                onClick={onRefresh}
              >
                {shared.refresh}
              </AppButton>
            )}
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {view && (
        <>
          <AppTabs
            id="employee"
            label={text.sections}
            tabs={[
              { id: "overview", label: text.overview },
              ...(canReadHistory ? [{ id: "history", label: text.history }] : []),
            ]}
            selected={tab}
            onSelected={onTab}
          />
          <div
            className="app-report-content app-tab-panel"
            id="employee-panel"
            role="tabpanel"
            aria-labelledby={`employee-tab-${tab}`}
          >
            {tab === "history" ? (
              history
            ) : (
              <>
                <AppPropertyList title={text.employment} items={view} />
                <Text className="app-muted">{text.profileNote}</Text>
              </>
            )}
          </div>
        </>
      )}
    </section>
  );
}
