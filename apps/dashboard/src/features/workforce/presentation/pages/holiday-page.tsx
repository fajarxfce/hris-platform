import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { holidayMessages } from "../i18n/holiday-messages";
import type { HolidayState } from "../models/holiday-state";
import type { holidayView } from "../models/holiday-view";

export function HolidayPage(props: {
  state: HolidayState;
  view: ReturnType<typeof holidayView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  editTo: string | null;
  onRefresh: () => void;
}) {
  const text = holidayMessages(props.locale);
  const shared = messages(props.locale);
  return (
    <section aria-busy={props.state.stage === "loading"}>
      <AppPageHeader
        title={props.state.holiday?.name ?? text.details}
        context={`${props.companyName} / ${text.title}`}
        actions={
          <>
            <Link to={props.backTo}>{text.back}</Link>
            {props.editTo && <Link to={props.editTo}>{text.edit}</Link>}
            <AppButton disabled={props.state.stage === "loading"} onClick={props.onRefresh}>
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={props.state.failure} locale={props.locale} />
      {props.state.stage === "loading" && <AppLoading label={shared.loading} />}
      {props.view && (
        <div className="app-report-content">
          <AppPropertyList title={text.overview} items={props.view} />
          <Text>{text.calendarPolicy}</Text>
        </div>
      )}
    </section>
  );
}
