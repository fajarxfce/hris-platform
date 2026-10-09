import { Dropdown, Field, Option } from "@fluentui/react-components";
import { forwardRef } from "react";

type Props = {
  label: string;
  value: readonly string[];
  options: readonly Readonly<{ value: string; label: string }>[];
  displayValue: string;
  placeholder: string;
  error?: string | undefined;
  onChange: (values: string[]) => void;
};

export const AppMultiSelect = forwardRef<HTMLButtonElement, Props>(function AppMultiSelect(
  { label, value, options, displayValue, placeholder, error, onChange },
  ref,
) {
  return (
    <Field
      className="app-multi-select"
      label={label}
      validationMessage={error ?? null}
      validationState={error ? "error" : "none"}
    >
      <Dropdown
        className="app-multi-select-control"
        multiselect
        button={{ ref }}
        value={displayValue}
        selectedOptions={[...value]}
        placeholder={placeholder}
        onOptionSelect={(_, data) => onChange(data.selectedOptions)}
      >
        {options.map((option) => (
          <Option key={option.value} value={option.value} text={option.label}>
            {option.label}
          </Option>
        ))}
      </Dropdown>
    </Field>
  );
});
