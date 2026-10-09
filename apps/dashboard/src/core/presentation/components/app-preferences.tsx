import { Select } from "@fluentui/react-components";
import { type Locale, messages } from "../i18n/messages";
import { AppButton } from "./app-button";

type Props = {
  locale: Locale;
  dark: boolean;
  onLocaleChanged: (locale: Locale) => void;
  onThemeChanged: () => void;
};

export function AppPreferences(props: Props) {
  const text = messages(props.locale);
  return (
    <div className="app-preferences">
      <Select
        aria-label={text.language}
        value={props.locale}
        onChange={(_, data) => props.onLocaleChanged(data.value === "id" ? "id" : "en")}
      >
        <option value="en">English</option>
        <option value="id">Indonesia</option>
      </Select>
      <AppButton appearance="subtle" onClick={props.onThemeChanged} aria-label={text.theme}>
        {props.dark ? text.light : text.dark}
      </AppButton>
    </div>
  );
}
