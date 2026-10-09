import type { Locale } from "../../../../core/presentation/i18n/messages";

const english = {
  headcount: "Headcount",
  asOf: "As of date",
  filters: "Report filters",
  apply: "Apply",
  employments: "Employment records",
  persons: "Unique people",
  statuses: "Employment status",
  contracts: "Contract type",
  active: "Active",
  probation: "Probation",
  suspended: "Suspended",
  permanent: "Permanent",
  fixedTerm: "Fixed term",
  definition:
    "Includes active, probationary, and suspended employment. Backdated corrections may change earlier reports.",
} as const;

type ReportingMessages = { readonly [K in keyof typeof english]: string };
const indonesian: ReportingMessages = {
  headcount: "Headcount",
  asOf: "Tanggal laporan",
  filters: "Filter laporan",
  apply: "Terapkan",
  employments: "Hubungan kerja",
  persons: "Jumlah orang",
  statuses: "Status kerja",
  contracts: "Jenis kontrak",
  active: "Aktif",
  probation: "Masa percobaan",
  suspended: "Diskors",
  permanent: "Tetap",
  fixedTerm: "Kontrak",
  definition:
    "Mencakup hubungan kerja aktif, masa percobaan, dan skorsing. Koreksi backdated dapat mengubah laporan sebelumnya.",
};

export function reportingMessages(locale: Locale): ReportingMessages {
  return locale === "id" ? indonesian : english;
}
