import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { CompanyAccess, CompanyMembership } from "../../../identity/domain/entities/session";
import { type ReportKind, reportDefinitions } from "../../domain/entities/report-catalogue";
import type { ReportFilterDefinition } from "../../domain/entities/saved-report-filter";
import { isHeadcountCompanies } from "../../domain/policies/headcount-policy";
import { canReadReportKind } from "../../domain/policies/report-catalogue-policy";
import { savedReportFilterMessages } from "../i18n/saved-report-filter-messages";
import type { ReportFilterEditorState } from "../models/report-filter-editor-state";
import type { ReportFilterEditorController } from "./report-filter-editor-controller";

type Values = {
  name: string;
  kind: ReportKind;
  asOf: string;
  from: string;
  until: string;
  status: string;
  companies: CompanyId[];
};
export function useReportFilterForm(
  controller: ReportFilterEditorController,
  state: ReportFilterEditorState,
  seed: ReportFilterDefinition,
  access: CompanyAccess,
  available: readonly CompanyMembership[],
  today: string,
  locale: Locale,
) {
  const definition = state.filter ?? seed;
  const values = useMemo<Values>(
    () => ({
      name: definition.name,
      kind: definition.kind,
      asOf: definition.parameters.asOf ?? today,
      from: definition.parameters.from ?? today,
      until: definition.parameters.until ?? today,
      status: definition.parameters.status ?? "",
      companies: [...definition.parameters.companies],
    }),
    [definition, today],
  );
  const form = useForm<Values>({ values });
  const name = useController({ name: "name", control: form.control });
  const kind = useController({ name: "kind", control: form.control });
  const asOf = useController({ name: "asOf", control: form.control });
  const from = useController({ name: "from", control: form.control });
  const until = useController({ name: "until", control: form.control });
  const status = useController({ name: "status", control: form.control });
  const companies = useController({
    name: "companies",
    control: form.control,
    rules: { validate: (ids, value) => value.kind !== "HEADCOUNT" || isHeadcountCompanies(ids) },
  });
  const editable = state.stage === "editing";
  const depart = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "completed" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  const text = savedReportFilterMessages(locale);
  const options = [
    ...available.map((company) => ({
      value: company.id,
      label: `${company.name} (${company.code})`,
    })),
    ...companies.field.value
      .filter((id) => !available.some((company) => company.id === id))
      .map((id) => ({ value: id, label: `${text.unavailableCompany} (${id})` })),
  ];
  return {
    name: name.field,
    kind: kind.field,
    asOf: asOf.field,
    from: from.field,
    until: until.field,
    status: status.field,
    kinds: (Object.keys(reportDefinitions) as ReportKind[]).filter(
      (value) => value === kind.field.value || canReadReportKind(access.permissions, value),
    ),
    selectKind: (value: string) => {
      if (
        !editable ||
        !Object.hasOwn(reportDefinitions, value) ||
        !canReadReportKind(access.permissions, value as ReportKind)
      )
        return;
      const date =
        value === "ATTENDANCE_CLOSINGS" || value === "PAYROLL_COSTS" ? today.slice(0, 7) : today;
      kind.field.onChange(value);
      for (const key of ["from", "until"] as const) form.setValue(key, date, { shouldDirty: true });
      form.setValue("asOf", today, { shouldDirty: true });
      form.setValue("status", "", { shouldDirty: true });
      form.setValue("companies", value === "HEADCOUNT" ? [access.companyId] : [], {
        shouldDirty: true,
      });
    },
    editable,
    canSave: editable && canReadReportKind(access.permissions, kind.field.value),
    month: kind.field.value === "ATTENDANCE_CLOSINGS" || kind.field.value === "PAYROLL_COSTS",
    companies: {
      ...companies.field,
      options,
      invalid: !!companies.fieldState.error,
      displayValue: `${companies.field.value.length} ${text.selected}`,
      onChange: (ids: string[]) => {
        if (
          editable &&
          ids.length <= 32 &&
          ids.every((id) => options.some((option) => option.value === id))
        )
          companies.field.onChange(ids);
      },
    },
    submit: form.handleSubmit((value) =>
      controller.save({
        name: value.name,
        kind: value.kind,
        definitionVersion: reportDefinitions[value.kind],
        parameters: {
          asOf: value.kind === "HEADCOUNT" ? value.asOf : null,
          from: value.kind === "HEADCOUNT" ? null : value.from,
          until: value.kind === "HEADCOUNT" ? null : value.until,
          status: value.kind === "OVERTIME" && value.status !== "" ? value.status : null,
          companies: value.kind === "HEADCOUNT" ? value.companies : [],
        },
      }),
    ),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
