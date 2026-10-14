import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { taxOpeningMessages } from "../i18n/tax-opening-messages";
import type { taxOpeningView } from "../models/tax-opening-view";
export function TaxOpeningProperties({
  view,
  locale,
}: {
  view: ReturnType<typeof taxOpeningView>;
  locale: Locale;
}) {
  return (
    <div className="app-report-content">
      <AppPropertyList title={view.title} items={view.properties} />
      <AppPropertyList title={taxOpeningMessages(locale).amounts} items={view.history} />
    </div>
  );
}
