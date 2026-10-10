import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  ApprovalTemplate,
  ApprovalTemplatePage,
} from "../../domain/entities/approval-template";
import { approvalMessages } from "../i18n/approval-messages";
import {
  approvalPermissionLabel,
  approvalTemplateMessages,
} from "../i18n/approval-template-messages";

export function approvalTemplatesView(page: ApprovalTemplatePage, locale: Locale) {
  const text = approvalTemplateMessages(locale);
  return page.items.map((item) => ({
    id: item.id,
    actionLabel: item.name,
    cells: [
      item.name,
      item.active ? text.active : text.inactive,
      item.effectiveFrom,
      String(item.revision),
    ],
  }));
}
export function approvalTemplateView(template: ApprovalTemplate, locale: Locale) {
  const text = approvalTemplateMessages(locale);
  const approval = approvalMessages(locale);
  return {
    properties: [
      { label: approval.kind, value: approval[template.kind] },
      { label: text.active, value: template.active ? text.yes : text.no },
      { label: text.currentVersion, value: String(template.version) },
      { label: text.revision, value: String(template.revision) },
      { label: text.effectiveFrom, value: template.effectiveFrom },
      { label: text.category, value: template.category ?? "—" },
      { label: text.minimumAmount, value: template.minimumAmount },
    ],
    stages: template.stages.map((stage, index) => ({
      id: String(index),
      cells: [
        String(index + 1),
        text[stage.assignment],
        stage.assignment === "NAMED"
          ? stage.accountIds.join(", ")
          : stage.permission
            ? approvalPermissionLabel(stage.permission, locale)
            : "—",
      ],
    })),
  };
}
