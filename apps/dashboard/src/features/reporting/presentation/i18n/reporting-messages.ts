import type { Locale } from "../../../../core/presentation/i18n/messages";

const english = {
  attendanceTitle: "Attendance report",
  from: "From date",
  until: "Until date",
  byDate: "By work date",
  workDate: "Work date",
  affectedDays: "Employee days with exceptions",
  affectedEmployments: "Employment records affected",
  pendingDays: "Employee days awaiting review",
  incompleteDays: "Employee days with incomplete clocks",
  pendingEvents: "Events awaiting review",
  observedDays: "Employee days with evidence",
  viewExceptions: "View exceptions",
  attendanceDefinition:
    "Counts are per employment and work date. Pending and incomplete clocks can overlap. Reviews and corrections can change earlier results; days without evidence do not imply absence.",
  headcount: "Headcount",
  companies: "Companies",
  company: "Company",
  companyGroup: "Company group",
  selectedCompanies: "companies selected",
  selectCompanies: "Select companies",
  companyBreakdown: "By company",
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
  attendanceTitle: "Laporan presensi",
  from: "Dari tanggal",
  until: "Sampai tanggal",
  byDate: "Per tanggal kerja",
  workDate: "Tanggal kerja",
  affectedDays: "Hari karyawan dengan masalah",
  affectedEmployments: "Hubungan kerja terdampak",
  pendingDays: "Hari karyawan menunggu review",
  incompleteDays: "Hari karyawan dengan clock belum lengkap",
  pendingEvents: "Bukti menunggu review",
  observedDays: "Hari karyawan dengan bukti",
  viewExceptions: "Lihat masalah",
  attendanceDefinition:
    "Hitungan per hubungan kerja dan tanggal kerja. Bukti tertunda dan clock belum lengkap dapat tumpang tindih. Review dan koreksi dapat mengubah hasil sebelumnya; hari tanpa bukti tidak berarti absen.",
  headcount: "Headcount",
  companies: "Perusahaan",
  company: "Perusahaan",
  companyGroup: "Grup perusahaan",
  selectedCompanies: "perusahaan dipilih",
  selectCompanies: "Pilih perusahaan",
  companyBreakdown: "Per perusahaan",
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
