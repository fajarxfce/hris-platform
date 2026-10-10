import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";

export function MfaRecoveryContent({
  codes,
  onSaved,
  locale,
}: {
  codes: readonly string[];
  onSaved: () => void;
  locale: Locale;
}) {
  const text = messages(locale);
  return (
    <div className="app-form">
      <Text>{text.recoveryDescription}</Text>
      <ul className="app-recovery-codes">
        {codes.map((code) => (
          <li key={code}>
            <code>{code}</code>
          </li>
        ))}
      </ul>
      <AppButton appearance="primary" onClick={onSaved}>
        {text.recoverySaved}
      </AppButton>
    </div>
  );
}
