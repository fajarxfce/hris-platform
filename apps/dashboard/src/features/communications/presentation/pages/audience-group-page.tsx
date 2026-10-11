import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAudienceGroup } from "../controllers/use-audience-group";
import { audienceGroupMessages } from "../i18n/audience-group-messages";
import type { audienceGroupView } from "../models/audience-group-view";

export function AudienceGroupPage(props: {
  state: ReturnType<typeof useAudienceGroup>;
  properties: ReturnType<typeof audienceGroupView>;
  locale: Locale;
  companyName: string;
  backTo: string;
  editTo: string | null;
  previousTo: string | null;
  currentTo: string | null;
  members: ReactNode;
}) {
  const text = audienceGroupMessages(props.locale);
  return (
    <section aria-busy={props.state.loading}>
      <AppPageHeader
        title={props.state.group?.name ?? text.details}
        context={props.companyName}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            {props.editTo && <Link to={props.editTo}>{text.edit}</Link>}
            {props.previousTo && <Link to={props.previousTo}>{text.previousRevision}</Link>}
            {props.currentTo && <Link to={props.currentTo}>{text.current}</Link>}
            <AppButton disabled={props.state.loading} onClick={props.state.refresh}>
              {messages(props.locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={props.state.failure} locale={props.locale} />
      <div className="app-report-content">
        {props.state.loading && <AppLoading label={messages(props.locale).loading} />}
        {props.state.group && (
          <>
            <AppPropertyList title={text.details} items={props.properties} />
            <p>{text.currentLabels}</p>
            {props.members}
          </>
        )}
      </div>
    </section>
  );
}
