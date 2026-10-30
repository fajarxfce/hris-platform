import type { ComponentProps } from "react";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { documentCategories } from "../../domain/entities/document-metadata";
import { documentMetadataMessages } from "../i18n/document-metadata-messages";

type TextField = Omit<ComponentProps<typeof AppTextField>, "label">;
export function DocumentMetadataFields(props: {
  locale: Locale;
  editable: boolean;
  category: Omit<ComponentProps<typeof AppSelect>, "label">;
  identityNumber: TextField;
  issuingCountry: TextField;
  issuedOn: TextField;
  validUntil: TextField;
}) {
  const text = documentMetadataMessages(props.locale);
  return (
    <fieldset className="app-document-file">
      <legend>{text.title}</legend>
      <p>{text.hint}</p>
      <AppSelect label={text.category} {...props.category} disabled={!props.editable}>
        {documentCategories.map((category) => (
          <option key={category} value={category}>
            {text.categories[category]}
          </option>
        ))}
      </AppSelect>
      <AppTextField
        label={text.identityNumber}
        {...props.identityNumber}
        maxLength={120}
        readOnly={!props.editable}
      />
      <AppTextField
        label={text.issuingCountry}
        hint={text.countryHint}
        {...props.issuingCountry}
        maxLength={2}
        readOnly={!props.editable}
      />
      <AppTextField
        label={text.issuedOn}
        {...props.issuedOn}
        type="date"
        min="1900-01-01"
        max="2200-12-31"
        readOnly={!props.editable}
      />
      <AppTextField
        label={text.validUntil}
        {...props.validUntil}
        type="date"
        min="1900-01-01"
        max="2200-12-31"
        readOnly={!props.editable}
      />
    </fieldset>
  );
}
