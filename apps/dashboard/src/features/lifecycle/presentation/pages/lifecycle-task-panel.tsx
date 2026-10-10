import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";

export function LifecycleTaskPanel({
  title,
  properties,
  locale,
  onClose,
}: {
  title: string;
  properties: readonly Readonly<{ label: string; value: string }>[];
  locale: Locale;
  onClose: () => void;
}) {
  const text = lifecycleCaseMessages(locale);
  return (
    <AppDetailsPanel open={true} title={title} closeLabel={text.close} onClose={onClose}>
      <AppPropertyList title={text.task} items={properties} />
    </AppDetailsPanel>
  );
}
