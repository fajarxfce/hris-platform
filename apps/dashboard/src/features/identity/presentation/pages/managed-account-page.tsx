import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import type { ManagedAccountState } from "../models/managed-account-state";
import type { managedAccountProperties } from "../models/managed-account-view";

export function ManagedAccountPage({
  state,
  properties,
  locale,
  backTo,
  editTo,
  onRefresh,
}: {
  state: ManagedAccountState;
  properties: ReturnType<typeof managedAccountProperties>;
  locale: Locale;
  backTo: string;
  editTo: string;
  onRefresh: () => void;
}) {
  const text = managedAccountMessages(locale);
  return (
    <section
      className="app-report-content app-platform-account"
      aria-busy={state.stage === "loading"}
    >
      <AppPageHeader
        title={state.review?.account.displayName ?? text.details}
        context={text.title}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {state.review && <Link to={editTo}>{text.edit}</Link>}
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              aria-label={messages(locale).refresh}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            />
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.review && (
        <>
          <AppPropertyList title={text.details} items={properties} />
          <p>{text.platformHint}</p>
        </>
      )}
    </section>
  );
}
