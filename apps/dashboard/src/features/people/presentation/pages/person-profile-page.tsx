import { Text, useRestoreFocusTarget } from "@fluentui/react-components";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTabs } from "../../../../core/presentation/components/app-tabs";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { personProfileMessages } from "../i18n/person-profile-messages";
import type { PersonProfileState } from "../models/person-profile-state";
import type { personProfileView } from "../models/person-profile-view";

export function PersonProfilePage({
  state,
  view,
  companyName,
  locale,
  backTo,
  editTo,
  owned,
  tab,
  canReadHistory,
  history,
  onTab,
  onRefresh,
}: {
  state: PersonProfileState;
  view: ReturnType<typeof personProfileView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  editTo: string | null;
  owned: boolean;
  tab: "overview" | "history";
  canReadHistory: boolean;
  history: ReactNode;
  onTab: (tab: string) => void;
  onRefresh: () => void;
}) {
  const text = personProfileMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.profile?.legalName ?? text.title}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link {...restore} to={backTo}>
              {text.back}
            </Link>
            {editTo && (
              <Link {...restore} to={editTo}>
                {text.edit}
              </Link>
            )}
            {(tab === "overview" || state.profile === null) && (
              <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
                {messages(locale).refresh}
              </AppButton>
            )}
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {view && (
        <>
          <AppTabs
            id="person-profile"
            label={text.sections}
            selected={tab}
            onSelected={onTab}
            tabs={[
              { id: "overview", label: text.overview },
              ...(canReadHistory ? [{ id: "history", label: text.history }] : []),
            ]}
          />
          <div
            className="app-report-content app-tab-panel"
            id="person-profile-panel"
            role="tabpanel"
            aria-labelledby={`person-profile-tab-${tab}`}
          >
            {tab === "history" ? (
              history
            ) : (
              <>
                <AppPropertyList title={text.privateData} items={view} />
                {!owned && <Text className="app-muted">{text.ownerNote}</Text>}
              </>
            )}
          </div>
        </>
      )}
    </section>
  );
}
