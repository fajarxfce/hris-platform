import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { shiftMessages } from "../i18n/shift-messages";
import type { ShiftState } from "../models/shift-state";
import type { shiftView } from "../models/shift-view";

export function ShiftPage(props: {
  state: ShiftState;
  view: ReturnType<typeof shiftView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  editTo: string | null;
  onRefresh: () => void;
}) {
  const text = shiftMessages(props.locale);
  const shared = messages(props.locale);
  return (
    <section aria-busy={props.state.stage === "loading"}>
      <AppPageHeader
        title={props.state.shift?.details.name ?? text.details}
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
          <AppPropertyList title={text.overview} items={props.view.overview} />
          <AppPropertyList title={text.location} items={props.view.location} />
          <Text>{text.snapshot}</Text>
        </div>
      )}
    </section>
  );
}
