import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";

const english = {
  title: "Payroll policy",
  configure: "Configure policy",
  edit: "New revision",
  back: "Back to policy",
  save: "Save policy",
  saved: "Policy saved.",
  view: "View policy",
  reload: "Load current policy",
  retrySave: "Retry save",
  month: "Payroll month",
  apply: "Apply",
  effective: "Effective policy",
  latest: "Latest saved revision",
  inspect: "View configuration",
  noPolicy: "No payroll policy configured.",
  noEffective: "No policy covers this month.",
  version: "Current version",
  revision: "Revision",
  from: "Effective from",
  until: "Effective through",
  currency: "Currency",
  taxRule: "Income tax rule",
  insuranceRule: "Insurance rule",
  minimum: "Minimum monthly wage (IDR)",
  healthCap: "Health contribution wage cap (IDR)",
  pensionCap: "Pension contribution wage cap (IDR)",
  rounding: "Contribution rounding",
  HALF_UP: "Nearest rupiah (half up)",
  UP: "Round up to rupiah",
  history: "Policy history",
  historyPages: "History pages",
  first: "First page",
  next: "Next page",
  inspectRevision: "View",
  recorded: "Recorded",
  actor: "Changed by",
  reason: "Reason",
  references: "Review references",
  evidence: "Revision evidence",
  close: "Close details",
  range: "Effective period",
  contributions: "Contribution bases",
  rules: "Rules",
  referenceHint: "One HTTPS reference per line, up to 10.",
  amountHint: "Decimal IDR amount, without thousands separators.",
  periodHint: "The effective period must stay within one calendar year.",
  basedOn: "Based on version",
  operation: "Operation",
  unconfirmed: "The save result is unconfirmed. Retry to recover the original result.",
};
const indonesian: typeof english = {
  title: "Kebijakan payroll",
  configure: "Atur kebijakan",
  edit: "Revisi baru",
  back: "Kembali ke kebijakan",
  save: "Simpan kebijakan",
  saved: "Kebijakan disimpan.",
  view: "Lihat kebijakan",
  reload: "Muat kebijakan terkini",
  retrySave: "Ulangi penyimpanan",
  month: "Bulan payroll",
  apply: "Terapkan",
  effective: "Kebijakan berlaku",
  latest: "Revisi terakhir disimpan",
  inspect: "Lihat konfigurasi",
  noPolicy: "Kebijakan payroll belum diatur.",
  noEffective: "Tidak ada kebijakan yang berlaku pada bulan ini.",
  version: "Versi terkini",
  revision: "Revisi",
  from: "Berlaku mulai",
  until: "Berlaku sampai",
  currency: "Mata uang",
  taxRule: "Aturan pajak penghasilan",
  insuranceRule: "Aturan asuransi",
  minimum: "Upah minimum bulanan (IDR)",
  healthCap: "Batas upah iuran kesehatan (IDR)",
  pensionCap: "Batas upah iuran pensiun (IDR)",
  rounding: "Pembulatan iuran",
  HALF_UP: "Rupiah terdekat (half up)",
  UP: "Bulatkan ke atas ke rupiah",
  history: "Riwayat kebijakan",
  historyPages: "Halaman riwayat",
  first: "Halaman pertama",
  next: "Halaman berikutnya",
  inspectRevision: "Lihat",
  recorded: "Dicatat",
  actor: "Diubah oleh",
  reason: "Alasan",
  references: "Referensi peninjauan",
  evidence: "Bukti revisi",
  close: "Tutup detail",
  range: "Periode berlaku",
  contributions: "Dasar iuran",
  rules: "Aturan",
  referenceHint: "Satu referensi HTTPS per baris, maksimal 10.",
  amountHint: "Nominal IDR desimal, tanpa pemisah ribuan.",
  periodHint: "Periode berlaku harus berada dalam satu tahun kalender.",
  basedOn: "Berdasarkan versi",
  operation: "Operasi",
  unconfirmed:
    "Hasil penyimpanan belum terkonfirmasi. Ulangi untuk memulihkan hasil operasi semula.",
};
export const payrollPolicyMessages = (locale: Locale) => (locale === "id" ? indonesian : english);

export function payrollPolicyFieldError(
  failure: Failure | null,
  field: string,
  locale: Locale,
): string | undefined {
  const code =
    failure?.fields[field] ??
    Object.entries(failure?.fields ?? {}).find(([key]) => key.startsWith(`${field}[`))?.[1];
  if (!code) return undefined;
  if (code === "below_minimum")
    return locale === "id"
      ? "Batas upah kesehatan tidak boleh di bawah upah minimum."
      : "The health wage cap must be at least the minimum wage.";
  if (field === "effectiveUntil") return payrollPolicyMessages(locale).periodHint;
  if (field === "reviewReferences")
    return locale === "id"
      ? "Isi 1–10 referensi HTTPS yang valid, maksimal 500 karakter per referensi."
      : "Enter 1–10 valid HTTPS references, up to 500 characters each.";
  if (["minimumMonthlyWage", "healthWageCap", "pensionWageCap"].includes(field))
    return locale === "id"
      ? "Isi nominal di atas nol, maksimal IDR 50.000.000.000 dengan dua angka desimal."
      : "Enter an amount above zero, up to IDR 50,000,000,000 with two decimal places.";
  if (field === "reason")
    return locale === "id"
      ? "Isi alasan, maksimal 1.000 karakter."
      : "Enter a reason, up to 1,000 characters.";
  return locale === "id" ? "Periksa nilai ini." : "Check this value.";
}
